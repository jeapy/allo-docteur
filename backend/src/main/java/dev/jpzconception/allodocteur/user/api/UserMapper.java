package dev.jpzconception.allodocteur.user.api;

import dev.jpzconception.allodocteur.user.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "twoFactorEnabled", target = "mfaEnabled")
    UserDto toDto(User user);

    UserSummary toSummary(User user);
}
