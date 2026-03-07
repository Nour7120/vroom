package com.county_cars.vroom.modules.authorization.service.impl;

import com.county_cars.vroom.modules.authorization.dto.request.GroupRequest;
import com.county_cars.vroom.modules.authorization.dto.request.PermissionRequest;
import com.county_cars.vroom.modules.authorization.dto.request.UserGroupRequest;
import com.county_cars.vroom.modules.authorization.dto.response.GroupResponse;
import com.county_cars.vroom.modules.authorization.dto.response.PermissionResponse;
import com.county_cars.vroom.modules.authorization.dto.response.UserGroupResponse;
import com.county_cars.vroom.modules.authorization.entity.Group;
import com.county_cars.vroom.modules.authorization.entity.Permission;
import com.county_cars.vroom.modules.authorization.entity.UserGroup;
import com.county_cars.vroom.modules.authorization.mapper.AuthorizationMapper;
import com.county_cars.vroom.modules.authorization.repository.GroupRepository;
import com.county_cars.vroom.modules.authorization.repository.PermissionRepository;
import com.county_cars.vroom.modules.authorization.repository.UserGroupRepository;
import com.county_cars.vroom.modules.authorization.service.AuthorizationService;
import com.county_cars.vroom.common.exception.ConflictException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.modules.keycloak.CurrentUserService;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import com.county_cars.vroom.modules.user_profile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthorizationServiceImpl implements AuthorizationService {

    private final PermissionRepository permissionRepository;
    private final GroupRepository groupRepository;
    private final UserGroupRepository userGroupRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuthorizationMapper authorizationMapper;
    private final CurrentUserService currentUserService;

    // ─── Permission ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PermissionResponse createPermission(PermissionRequest request) {
        if (permissionRepository.existsByName(request.getName())) {
            throw new ConflictException("Permission already exists: " + request.getName());
        }
        Permission entity = authorizationMapper.toPermissionEntity(request);
        return authorizationMapper.toPermissionResponse(permissionRepository.save(entity));
    }

    @Override
    public PermissionResponse getPermissionById(Long id) {
        return authorizationMapper.toPermissionResponse(findPermissionById(id));
    }

    @Override
    public Page<PermissionResponse> getAllPermissions(Pageable pageable) {
        return permissionRepository.findAll(pageable)
                .map(authorizationMapper::toPermissionResponse);
    }

    @Override
    @Transactional
    public PermissionResponse updatePermission(Long id, PermissionRequest request) {
        Permission entity = findPermissionById(id);
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        return authorizationMapper.toPermissionResponse(permissionRepository.save(entity));
    }

    @Override
    @Transactional
    public void deletePermission(Long id) {
        Permission entity = findPermissionById(id);
        entity.setIsDeleted(Boolean.TRUE);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setDeletedBy(currentUserService.getCurrentKeycloakUserId());
        permissionRepository.save(entity);
    }

    // ─── Group ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public GroupResponse createGroup(GroupRequest request) {
        if (groupRepository.existsByName(request.getName())) {
            throw new ConflictException("Group already exists: " + request.getName());
        }
        Group entity = authorizationMapper.toGroupEntity(request);
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            Set<Permission> permissions = resolvePermissions(request.getPermissionIds());
            entity.setPermissions(permissions);
        }
        return authorizationMapper.toGroupResponse(groupRepository.save(entity));
    }

    @Override
    public GroupResponse getGroupById(Long id) {
        return authorizationMapper.toGroupResponse(findGroupById(id));
    }

    @Override
    public Page<GroupResponse> getAllGroups(Pageable pageable) {
        return groupRepository.findAll(pageable).map(authorizationMapper::toGroupResponse);
    }

    @Override
    @Transactional
    public GroupResponse updateGroup(Long id, GroupRequest request) {
        Group entity = findGroupById(id);
        authorizationMapper.updateGroupFromRequest(request, entity);
        if (request.getPermissionIds() != null) {
            entity.setPermissions(resolvePermissions(request.getPermissionIds()));
        }
        return authorizationMapper.toGroupResponse(groupRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteGroup(Long id) {
        Group entity = findGroupById(id);
        entity.setIsDeleted(Boolean.TRUE);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setDeletedBy(currentUserService.getCurrentKeycloakUserId());
        groupRepository.save(entity);
    }

    // ─── UserGroup ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserGroupResponse assignUserToGroup(UserGroupRequest request) {
        if (userGroupRepository.existsByUserProfileIdAndGroupIdAndIsDeletedFalse(
                request.getUserProfileId(), request.getGroupId())) {
            throw new ConflictException("User is already in this group");
        }
        UserProfile userProfile = userProfileRepository.findById(request.getUserProfileId())
                .orElseThrow(() -> new NotFoundException("UserProfile not found: " + request.getUserProfileId()));
        Group group = findGroupById(request.getGroupId());

        UserGroup membership = UserGroup.builder()
                .userProfile(userProfile)
                .group(group)
                .build();
        return authorizationMapper.toUserGroupResponse(userGroupRepository.save(membership));
    }

    @Override
    public Page<UserGroupResponse> getUserGroups(Long userProfileId, Pageable pageable) {
        return userGroupRepository.findAllByUserProfileIdAndIsDeletedFalse(userProfileId, pageable)
                .map(authorizationMapper::toUserGroupResponse);
    }

    @Override
    public Page<UserGroupResponse> getGroupMembers(Long groupId, Pageable pageable) {
        return userGroupRepository.findAllByGroupIdAndIsDeletedFalse(groupId, pageable)
                .map(authorizationMapper::toUserGroupResponse);
    }

    @Override
    @Transactional
    public void removeUserFromGroup(Long userProfileId, Long groupId) {
        UserGroup membership = userGroupRepository
                .findByUserProfileIdAndGroupIdAndIsDeletedFalse(userProfileId, groupId)
                .orElseThrow(() -> new NotFoundException("Membership not found"));
        membership.setIsDeleted(Boolean.TRUE);
        membership.setDeletedAt(LocalDateTime.now());
        membership.setDeletedBy(currentUserService.getCurrentKeycloakUserId());
        userGroupRepository.save(membership);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Permission findPermissionById(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Permission not found: " + id));
    }

    private Group findGroupById(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Group not found: " + id));
    }

    private Set<Permission> resolvePermissions(Set<Long> ids) {
        return ids.stream().map(this::findPermissionById).collect(Collectors.toSet());
    }
}


