package com.monglepick.monglepickbackend.domain.user.mapper;

import com.monglepick.monglepickbackend.domain.user.entity.WithdrawnUserIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 탈퇴 사용자 식별자 해시 MyBatis Mapper.
 */
@Mapper
public interface WithdrawnUserIdentityMapper {

    void insert(WithdrawnUserIdentity identity);

    boolean existsActiveBlock(@Param("identityType") String identityType,
                              @Param("identityHash") String identityHash,
                              @Param("now") LocalDateTime now);

    void deleteByWithdrawnUserId(@Param("withdrawnUserId") String withdrawnUserId);
}
