package com.monglepick.monglepickbackend.domain.community.service;

import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 이미지 업로드 서비스
 *
 * 【로컬 개발】
 *   - UPLOAD_DIR=./uploads (프로젝트 루트 기준 상대경로)
 *   - Spring Boot WebMvcConfig가 /images/** 를 ./uploads/ 로 서빙
 *
 * 【서버 배포】
 *   - UPLOAD_DIR=/home/ubuntu/data (.env로 오버라이드)
 *   - NGINX가 /images/** 를 /home/ubuntu/data/ 로 서빙
 *   - Spring Boot WebMvcConfig는 로컬에서만 동작 (서버에서는 NGINX가 담당)
 *
 * 【추후 S3/Object Storage 전환 시】
 *   - 이 서비스만 S3 업로드 로직으로 교체하면 됨
 *   - Controller, 프론트 코드는 변경 불필요
 *
 * 【보안 정책】 (2026-04-14 강화)
 *   1. MIME 헤더 + 확장자 + magic bytes 3중 검증 (헤더 단독 신뢰 X — 클라이언트 위조 가능)
 *   2. 확장자 화이트리스트 + 위험 확장자 블록(.jsp/.php/.html 등 RCE 경로 차단)
 *   3. userId 정규식 화이트리스트 + 최종 경로 startsWith() 검증 (Path Traversal 방어)
 *   4. UPLOAD_DIR 절대경로 강제 (상대경로 ".." 시작 시 기동 실패)
 */
@Slf4j
@Service
public class ImageService {

    /**
     * 파일 저장 경로
     * 로컬: ./uploads (기본값)
     * 서버: /home/ubuntu/data (.env UPLOAD_DIR으로 오버라이드)
     */
    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    /**
     * 이미지 접근 URL 접두사
     * 로컬: http://localhost:8080/images (Spring Boot 직접 서빙)
     * 서버: http://210.109.15.187/images (.env UPLOAD_URL_PREFIX으로 오버라이드, NGINX 서빙)
     */
    @Value("${app.upload.url-prefix:http://localhost:8080/images}")
    private String urlPrefix;

    // 허용 MIME 타입 (Content-Type 헤더 1차 검증)
    private static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    // 허용 확장자 화이트리스트 (소문자로 비교)
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp"
    );

    // 명시적으로 차단할 위험 확장자 (정상적인 이미지 업로드 경로에서는 절대 와선 안 됨)
    // - .jsp/.php/.asp/.aspx/.cgi → 서버 사이드 실행 경로 (NGINX/Apache 설정에 따라 RCE)
    // - .html/.htm/.svg → XSS 캐리어 (NGINX 가 정적 서빙하면 브라우저에서 실행)
    // - .exe/.bat/.sh/.cmd → 다운로드 후 실행 유도
    private static final Set<String> DENIED_EXTENSIONS = Set.of(
            ".jsp", ".jspx", ".php", ".php3", ".php4", ".php5", ".phtml",
            ".asp", ".aspx", ".cgi", ".pl", ".py", ".rb",
            ".html", ".htm", ".xhtml", ".svg",
            ".exe", ".bat", ".sh", ".cmd", ".com", ".dll", ".jar"
    );

    // userId 화이트리스트 (영문/숫자/하이픈/언더스코어만 허용 — 디렉토리 분리자/특수문자 차단)
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]{1,64}$");

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_FILE_COUNT = 5; // 최대 5장

    // Magic bytes 시그니처 (파일 헤더 검증) — Content-Type 헤더 위조 방어
    // JPEG: FF D8 FF
    // PNG : 89 50 4E 47 0D 0A 1A 0A
    // GIF : 47 49 46 38 (37|39) 61  (GIF87a / GIF89a)
    // WEBP: 52 49 46 46 .. .. .. .. 57 45 42 50  (RIFF....WEBP)
    private static final byte[] SIG_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] SIG_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] SIG_GIF87 = {0x47, 0x49, 0x46, 0x38, 0x37, 0x61};
    private static final byte[] SIG_GIF89 = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
    private static final byte[] SIG_RIFF = {0x52, 0x49, 0x46, 0x46}; // RIFF
    private static final byte[] SIG_WEBP = {0x57, 0x45, 0x42, 0x50}; // WEBP (offset 8)

    /**
     * 이미지 업로드
     * 파일을 저장하고 접근 가능한 URL 목록을 반환한다.
     *
     * @param files  업로드할 이미지 파일 목록
     * @param userId 업로드하는 사용자 ID (디렉토리 분리용)
     * @return 업로드된 이미지 URL 목록
     */
    public List<String> uploadImages(List<MultipartFile> files, String userId) {
        // userId 인증 검증 (Spring Security 가 통과시켰어도 방어적으로 한 번 더 확인)
        if (userId == null || userId.isBlank() || !USER_ID_PATTERN.matcher(userId).matches()) {
            // userId 가 패턴을 벗어나면 그 자체로 비정상 (정상 가입 userId 는 영문/숫자/언더스코어만 사용)
            // Path Traversal 방어 — userId 에 "../" 나 절대경로가 섞이지 못하도록 차단
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "유효한 사용자 정보가 필요합니다.");
        }

        // 업로드 루트 경로를 절대경로로 정규화하여 한 번만 계산 (이후 startsWith 비교의 기준)
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

        // 빈 파일은 제외하여 실제 업로드 대상만 카운트 (멀티파트 폼이 빈 슬롯 보낼 때 대비)
        List<MultipartFile> nonEmpty = new ArrayList<>(files.size());
        for (MultipartFile f : files) {
            if (f != null && !f.isEmpty()) nonEmpty.add(f);
        }
        if (nonEmpty.size() > MAX_FILE_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이미지는 최대 " + MAX_FILE_COUNT + "장까지 업로드 가능합니다.");
        }

        List<String> urls = new ArrayList<>(nonEmpty.size());

        for (MultipartFile file : nonEmpty) {

            // ── 1차: 파일 크기 검증 (서버 자원 보호 — 검증 비용이 가장 싸므로 먼저) ──
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "파일 크기는 10MB 이하여야 합니다.");
            }

            // ── 2차: Content-Type 헤더 검증 (위조 가능하므로 단독 신뢰 X, 후속 검증과 합산) ──
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "JPG, PNG, GIF, WEBP 형식만 업로드 가능합니다.");
            }

            // ── 3차: 확장자 화이트리스트 + 위험 확장자 블록 ──
            String ext = getExtension(file.getOriginalFilename()); // 안전한 확장자만 반환 (그 외 차단)

            // ── 4차: 파일 본문 magic bytes 검증 (헤더/확장자 위조에 대한 최종 방어선) ──
            if (!matchesImageMagicBytes(file)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "파일 내용이 이미지가 아닙니다. (헤더 시그니처 불일치)");
            }

            // ── 5차: 파일명은 UUID 로 무작위화 — 원본 filename 은 확장자 추출에만 사용 ──
            String filename = UUID.randomUUID() + ext;

            // userId 디렉토리 분리 (위에서 USER_ID_PATTERN 통과했으므로 안전)
            Path dirPath = uploadPath.resolve(userId);
            Path filePath = dirPath.resolve(filename);

            // ── 6차: Path Traversal 최종 검증 ──
            // 정상 흐름에서는 절대 발생하지 않지만, uploadDir 설정 오류나 미래 코드 변경에 대한 안전망.
            // .normalize() 후의 절대경로가 uploadPath 를 벗어나면 즉시 차단.
            Path normalizedFile = filePath.toAbsolutePath().normalize();
            if (!normalizedFile.startsWith(uploadPath)) {
                log.error("Path traversal 시도 차단 — userId={}, filename={}, resolvedPath={}",
                        userId, filename, normalizedFile);
                throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 파일 경로입니다.");
            }

            try {
                // 디렉토리가 없으면 자동 생성
                Files.createDirectories(dirPath);
                // 파일 저장 (transferTo 대신 Files.copy 사용 — Tomcat 임시경로 문제 방지)
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, normalizedFile,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                log.error("이미지 저장 실패 — userId: {}, filename: {}", userId, filename, e);
                throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 저장에 실패했습니다.");
            }

            // URL 생성: urlPrefix + /userId/파일명
            // 로컬: http://localhost:8080/images/userId/uuid.jpg
            // 서버: http://210.109.15.187/images/userId/uuid.jpg
            urls.add(urlPrefix + "/" + userId + "/" + filename);
            log.info("이미지 업로드 완료 — userId: {}, path: {}", userId, normalizedFile);
        }

        return urls;
    }

    /**
     * 안전한 확장자만 반환한다.
     * - 원본 filename 에서 경로 분리자(/, \) 를 제거한 뒤 마지막 점 이후를 추출.
     * - 위험 확장자(.jsp/.php 등) 는 즉시 예외.
     * - 화이트리스트(.jpg/.png/.gif/.webp) 외도 즉시 예외.
     * - filename 자체가 null/점 없음이면 ".jpg" 로 fallback (UUID 기반 저장이므로 충돌 없음).
     */
    private String getExtension(String filename) {
        if (filename == null || filename.isBlank()) return ".jpg";

        // 경로 분리자 제거 — Windows("\\") / Unix("/") 양쪽 모두 처리하기 위해
        // Paths.get(filename).getFileName() 사용 시 OS 의존성이 있으므로 직접 분리.
        String basename = filename;
        int slash = Math.max(basename.lastIndexOf('/'), basename.lastIndexOf('\\'));
        if (slash >= 0) basename = basename.substring(slash + 1);

        if (!basename.contains(".")) return ".jpg";

        String ext = basename.substring(basename.lastIndexOf(".")).toLowerCase();

        // 위험 확장자 즉시 차단 (정상 이미지 업로드 시나리오에선 절대 도달 X — 도달 시 공격 시도 가능성)
        if (DENIED_EXTENSIONS.contains(ext)) {
            log.warn("위험 확장자 업로드 차단 — original filename: {}, ext: {}", filename, ext);
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "허용되지 않은 파일 형식입니다.");
        }

        // 화이트리스트 외 차단
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "JPG, PNG, GIF, WEBP 형식만 업로드 가능합니다.");
        }

        // .jpeg → .jpg 정규화 (저장 파일명 일관성)
        return ext.equals(".jpeg") ? ".jpg" : ext;
    }

    /**
     * 파일 본문의 magic bytes 가 허용된 이미지 시그니처 중 하나와 일치하는지 검사한다.
     * Content-Type 헤더가 위조되었어도 실제 파일 내용이 이미지가 아니면 이 단계에서 차단된다.
     *
     * <p>스트림은 한 번만 읽도록 12바이트 헤더만 확인하며, 이후 저장 시 전체 스트림은 다시 받아온다
     * (MultipartFile.getInputStream() 은 반복 호출 가능).</p>
     */
    private boolean matchesImageMagicBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 8) return false; // 정상 이미지 헤더는 최소 8바이트 이상

            if (startsWith(header, SIG_JPEG)) return true;
            if (startsWith(header, SIG_PNG)) return true;
            if (startsWith(header, SIG_GIF87) || startsWith(header, SIG_GIF89)) return true;

            // WEBP: RIFF + 4바이트 크기 + WEBP
            if (read >= 12 && startsWith(header, SIG_RIFF)) {
                byte[] webpMarker = new byte[4];
                System.arraycopy(header, 8, webpMarker, 0, 4);
                if (startsWith(webpMarker, SIG_WEBP)) return true;
            }
            return false;
        } catch (IOException e) {
            log.warn("magic bytes 검증 실패 — 파일 읽기 오류", e);
            return false;
        }
    }

    private boolean startsWith(byte[] data, byte[] sig) {
        if (data.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) {
            if (data[i] != sig[i]) return false;
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Admin 자산 업로드 (2026-04-27 신설) — point_items 이미지 전용
    // ═══════════════════════════════════════════════════════════════

    /** Admin 자산 업로드용 허용 확장자 — SVG 포함. 사용자 업로드보다 신뢰도 높지만 별도 화이트리스트로 격리. */
    private static final Set<String> ADMIN_ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg"
    );

    /** Admin 자산 업로드 시 허용되는 subdir — point_items/{avatars,badges} 만. */
    private static final Set<String> ADMIN_ALLOWED_SUBDIRS = Set.of(
            "avatars", "badges"
    );

    /** SVG 내부에서 거부할 위험 패턴 (XSS 페이로드의 일반 형태). */
    private static final List<String> SVG_DANGEROUS_PATTERNS = List.of(
            "<script", "</script", "javascript:", "data:text/html",
            "onload=", "onclick=", "onerror=", "onmouseover=", "onfocus=",
            "<iframe", "<embed", "<object",
            "xlink:href=\"javascript:", "xlink:href='javascript:"
    );

    /**
     * Admin 자산(아바타·배지) 이미지 업로드.
     *
     * <p>일반 사용자 업로드({@link #uploadImages})와 달리 SVG 를 허용한다. Admin 인증이
     * 전제이므로 신뢰도가 높지만, SVG 의 XSS 위험을 줄이기 위해 본문에서 위험 패턴
     * (&lt;script&gt;, javascript:, on*= 등)을 검출하여 즉시 차단한다.</p>
     *
     * <p>저장 경로: {@code {uploadDir}/admin-assets/{subdir}/{uuid}.{ext}}<br>
     * 반환 URL : {@code {urlPrefix}/admin-assets/{subdir}/{filename}} (절대 URL)</p>
     *
     * <p>운영 보안 추가 권장: nginx 에서 {@code application/svg+xml} 응답에
     * {@code Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'} 강제.</p>
     *
     * @param file   업로드 파일
     * @param subdir "avatars" | "badges" — 그 외 값은 거부
     * @return 절대 URL (DB imageUrl 컬럼에 그대로 저장)
     */
    public String uploadAdminAssetImage(MultipartFile file, String subdir) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 파일이 없습니다.");
        }
        if (subdir == null || !ADMIN_ALLOWED_SUBDIRS.contains(subdir)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "subdir 은 'avatars' 또는 'badges' 만 허용됩니다.");
        }

        // ── 1차: 파일 크기 (Admin 용으로 5MB 한도 — SVG 는 보통 수 KB) ──
        final long ADMIN_MAX_SIZE = 5L * 1024 * 1024;
        if (file.getSize() > ADMIN_MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일 크기는 5MB 이하여야 합니다.");
        }

        // ── 2차: 확장자 추출 + 화이트리스트 (Admin 용 — SVG 포함) ──
        String ext = extractAdminAssetExtension(file.getOriginalFilename());

        // ── 3차: 본문 검증 분기 ──
        // SVG 는 magic bytes 가 아니라 XML 이므로 별도 본문 검사. 그 외는 기존 magic bytes 재사용.
        if (".svg".equals(ext)) {
            validateSvgContent(file);
        } else if (!matchesImageMagicBytes(file)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "파일 내용이 이미지가 아닙니다. (헤더 시그니처 불일치)");
        }

        // ── 4차: 저장 ──
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path adminDir = uploadPath.resolve("admin-assets").resolve(subdir);
        String filename = UUID.randomUUID() + ext;
        Path filePath = adminDir.resolve(filename);

        Path normalizedFile = filePath.toAbsolutePath().normalize();
        if (!normalizedFile.startsWith(uploadPath)) {
            log.error("[Admin] Path traversal 차단 — subdir={}, filename={}, resolved={}",
                    subdir, filename, normalizedFile);
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 파일 경로입니다.");
        }

        try {
            Files.createDirectories(adminDir);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, normalizedFile,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("[Admin] 자산 업로드 실패 — subdir={}, filename={}", subdir, filename, e);
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 저장에 실패했습니다.");
        }

        String url = urlPrefix + "/admin-assets/" + subdir + "/" + filename;
        log.info("[Admin] 자산 업로드 완료 — subdir={}, path={}, url={}", subdir, normalizedFile, url);
        return url;
    }

    /**
     * Admin 자산용 확장자 추출 — {@link #getExtension} 의 SVG 허용 변종.
     *
     * <p>일반 사용자 경로의 {@code DENIED_EXTENSIONS} 가 SVG 를 차단하는데, Admin 경로에서는
     * SVG 를 허용해야 하므로 별도 메서드로 분리. 위험 확장자 차단은 동일하게 유지.</p>
     */
    private String extractAdminAssetExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일명이 비어 있습니다.");
        }

        String basename = filename;
        int slash = Math.max(basename.lastIndexOf('/'), basename.lastIndexOf('\\'));
        if (slash >= 0) basename = basename.substring(slash + 1);

        if (!basename.contains(".")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "확장자가 필요합니다.");
        }

        String ext = basename.substring(basename.lastIndexOf(".")).toLowerCase();

        /* 위험 확장자 차단 — DENIED_EXTENSIONS 의 SVG 만 제외하고 그대로 적용. */
        for (String denied : DENIED_EXTENSIONS) {
            /* SVG 는 Admin 경로에서만 허용하므로 DENIED_EXTENSIONS 매칭에서 제외. */
            if (".svg".equals(denied)) continue;
            if (denied.equals(ext)) {
                log.warn("[Admin] 위험 확장자 업로드 차단 — original={}, ext={}", filename, ext);
                throw new BusinessException(ErrorCode.INVALID_INPUT, "허용되지 않은 파일 형식입니다.");
            }
        }

        if (!ADMIN_ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "JPG, PNG, GIF, WEBP, SVG 형식만 업로드 가능합니다.");
        }

        return ext.equals(".jpeg") ? ".jpg" : ext;
    }

    /**
     * SVG 본문에서 XSS 위험 패턴을 검출한다.
     *
     * <p>완전한 sanitization 은 아니지만, 일반적인 페이로드(&lt;script&gt;, javascript:,
     * on*= 이벤트 핸들러)를 모두 차단한다. 합법적인 디자인 SVG 에는 이들 패턴이 등장할
     * 이유가 없다.</p>
     */
    private void validateSvgContent(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] bytes = is.readAllBytes();
            String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).toLowerCase();

            /* SVG 의 기본 요건 — <svg 태그가 본문에 등장해야 한다. */
            if (!content.contains("<svg")) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "SVG 파일 형식이 올바르지 않습니다.");
            }

            for (String dangerous : SVG_DANGEROUS_PATTERNS) {
                if (content.contains(dangerous)) {
                    log.warn("[Admin] SVG 위험 패턴 검출 — pattern={}, filename={}",
                            dangerous, file.getOriginalFilename());
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            "SVG 파일에 허용되지 않은 요소가 포함되어 있습니다: " + dangerous);
                }
            }
        } catch (IOException e) {
            log.warn("[Admin] SVG 본문 검증 실패 — 파일 읽기 오류", e);
            throw new BusinessException(ErrorCode.INVALID_INPUT, "SVG 파일을 읽을 수 없습니다.");
        }
    }
}
