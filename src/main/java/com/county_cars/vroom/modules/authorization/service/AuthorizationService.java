package com.county_cars.vroom.modules.authorization.service;

import com.county_cars.vroom.modules.authorization.dto.request.GroupRequest;
import com.county_cars.vroom.modules.authorization.dto.request.PermissionRequest;
import com.county_cars.vroom.modules.authorization.dto.request.UserGroupRequest;
import com.county_cars.vroom.modules.authorization.dto.response.GroupResponse;
import com.county_cars.vroom.modules.authorization.dto.response.PermissionResponse;
import com.county_cars.vroom.modules.authorization.dto.response.UserGroupResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuthorizationService {

    // Permission
    PermissionResponse createPermission(PermissionRequest request);
    PermissionResponse getPermissionById(Long id);
    Page<PermissionResponse> getAllPermissions(Pageable pageable);
    PermissionResponse updatePermission(Long id, PermissionRequest request);
    void deletePermission(Long id);

    // Group
    GroupResponse createGroup(GroupRequest request);
    GroupResponse getGroupById(Long id);
    Page<GroupResponse> getAllGroups(Pageable pageable);
    GroupResponse updateGroup(Long id, GroupRequest request);
    void deleteGroup(Long id);

    // UserGroup
    UserGroupResponse assignUserToGroup(UserGroupRequest request);
    Page<UserGroupResponse> getUserGroups(Long userProfileId, Pageable pageable);
    Page<UserGroupResponse> getGroupMembers(Long groupId, Pageable pageable);
    void removeUserFromGroup(Long userProfileId, Long groupId);
}

