package com.monglepick.monglepickbackend.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRequestDTO {

        public interface existGroup {}   // 회원 존재 여부 확인
        public interface addGroup {}     // 회원 가입
        public interface passwordGroup {} // 비밀번호 변경
        public interface updateGroup {}  // 회원 정보 수정
        public interface deleteGroup {}  // 회원 탈퇴

        // 이메일을 아이디로 사용 (로그인 식별자)
        @NotBlank(groups = {existGroup.class, addGroup.class, updateGroup.class, deleteGroup.class})
        @Email(groups = {existGroup.class, addGroup.class, updateGroup.class, deleteGroup.class})
        private String useremail;

        // 비밀번호
        @NotBlank(groups = {addGroup.class, passwordGroup.class})
        @Size(min = 8, max = 16)
        private String userpassword;

        // 닉네임
        @NotBlank(groups = {addGroup.class, updateGroup.class})
        private String usernickname;
}