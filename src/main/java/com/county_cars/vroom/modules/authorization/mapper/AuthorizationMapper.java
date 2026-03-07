package com.county_cars.vroom.modules.authorization.mapper;

import com.county_cars.vroom.modules.authorization.dto.request.GroupRequest;
import com.county_cars.vroom.modules.authorization.dto.request.PermissionRequest;
import com.county_cars.vroom.modules.authorization.dto.response.GroupResponse;
import com.county_cars.vroom.modules.authorization.dto.response.PermissionResponse;
import com.county_cars.vroom.modules.authorization.dto.response.UserGroupResponse;
import com.county_cars.vroom.modules.authorization.entity.Group;
import com.county_cars.vroom.modules.authorization.entity.Permission;
import com.county_cars.vroom.modules.authorization.entity.UserGroup;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuthorizationMapper {

    Permission toPermissionEntity(PermissionRequest request);

    PermissionResponse toPermissionResponse(Permission entity);

    @Mapping(target = "permissions", ignore = true)
    Group toGroupEntity(GroupRequest request);

    GroupResponse toGroupResponse(Group entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "permissions", ignore = true)
    void updateGroupFromRequest(GroupRequest request, @MappingTarget Group entity);

    @Mapping(target = "userProfileId", source = "userProfile.id")
    @Mapping(target = "userEmail",     source = "userProfile.email")
    @Mapping(target = "groupId",       source = "group.id")
    @Mapping(target = "groupName",     source = "group.name")
    UserGroupResponse toUserGroupResponse(UserGroup entity);
}

