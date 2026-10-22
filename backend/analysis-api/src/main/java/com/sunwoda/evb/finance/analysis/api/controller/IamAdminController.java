package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.AdminMenuResponse;
import com.sunwoda.evb.finance.analysis.api.dto.AdminRoleResponse;
import com.sunwoda.evb.finance.analysis.api.dto.AdminUserResponse;
import com.sunwoda.evb.finance.analysis.api.dto.IamMenuRequest;
import com.sunwoda.evb.finance.analysis.api.dto.IamRoleRequest;
import com.sunwoda.evb.finance.analysis.api.dto.IamUserRequest;
import com.sunwoda.evb.finance.analysis.api.dto.RoleMenusRequest;
import com.sunwoda.evb.finance.analysis.api.dto.UserAuthorizationRequest;
import com.sunwoda.evb.finance.analysis.application.service.IamAdminService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.IamMenu;
import com.sunwoda.evb.finance.analysis.domain.model.IamRole;
import com.sunwoda.evb.finance.analysis.domain.model.IamUser;
import com.sunwoda.evb.finance.analysis.domain.model.RoleMenuAssignment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/** 本次新增：用户、角色、菜单和数据权限管理中心的 ADMIN API。 */
@RestController
@RequestMapping("/api/v1/admin")
public class IamAdminController {
    private final IamAdminService service;
    public IamAdminController(IamAdminService service) { this.service = service; }

    @GetMapping("/users")
    public ApiResult<List<AdminUserResponse>> users(@RequestParam(defaultValue = "false") boolean includeDisabled,
                                                    Principal principal) {
        String operator = requiredUser(principal);
        List<AdminUserResponse> result = new ArrayList<AdminUserResponse>();
        for (IamUser user : service.listUsers(operator, includeDisabled)) result.add(userResponse(operator, user));
        return ApiResult.ok(result);
    }
    @PostMapping("/users")
    public ApiResult<AdminUserResponse> createUser(@RequestBody IamUserRequest request, Principal principal) {
        String operator = requiredUser(principal);
        IamUserRequest value = required(request);
        IamUser saved = service.createUser(operator, value.toDomain(), value.getRoleCodes(), value.toScopeSelections());
        return ApiResult.ok(userResponse(operator, saved));
    }
    @PutMapping("/users")
    public ApiResult<AdminUserResponse> updateUser(@RequestBody IamUserRequest request, Principal principal) {
        String operator = requiredUser(principal);
        IamUserRequest value = required(request);
        IamUser saved = service.updateUser(operator, value.toDomain(), value.getRoleCodes(), value.toScopeSelections());
        return ApiResult.ok(userResponse(operator, saved));
    }

    @GetMapping("/roles")
    public ApiResult<List<AdminRoleResponse>> roles(@RequestParam(defaultValue = "false") boolean includeDisabled,
                                                    Principal principal) {
        String operator = requiredUser(principal);
        List<AdminRoleResponse> result = new ArrayList<AdminRoleResponse>();
        for (IamRole role : service.listRoles(operator, includeDisabled)) result.add(roleResponse(operator, role));
        return ApiResult.ok(result);
    }
    @PostMapping("/roles")
    public ApiResult<AdminRoleResponse> createRole(@RequestBody IamRoleRequest request, Principal principal) {
        String operator = requiredUser(principal);
        IamRoleRequest value = required(request);
        IamRole saved = service.createRole(operator, value.toDomain(), value.getMenuCodes());
        return ApiResult.ok(roleResponse(operator, saved));
    }
    @PutMapping("/roles")
    public ApiResult<AdminRoleResponse> updateRole(@RequestBody IamRoleRequest request, Principal principal) {
        String operator = requiredUser(principal);
        IamRoleRequest value = required(request);
        IamRole saved = service.updateRole(operator, value.toDomain(), value.getMenuCodes());
        return ApiResult.ok(roleResponse(operator, saved));
    }

    @GetMapping("/menus")
    public ApiResult<List<AdminMenuResponse>> menus(@RequestParam(defaultValue = "false") boolean includeDisabled,
                                                    Principal principal) {
        List<AdminMenuResponse> result = new ArrayList<AdminMenuResponse>();
        for (IamMenu menu : service.listMenus(requiredUser(principal), includeDisabled)) {
            result.add(new AdminMenuResponse(menu));
        }
        return ApiResult.ok(result);
    }
    @PostMapping("/menus")
    public ApiResult<AdminMenuResponse> createMenu(@RequestBody IamMenuRequest request, Principal principal) {
        return ApiResult.ok(new AdminMenuResponse(service.createMenu(requiredUser(principal), required(request).toDomain())));
    }
    @PutMapping("/menus")
    public ApiResult<AdminMenuResponse> updateMenu(@RequestBody IamMenuRequest request, Principal principal) {
        return ApiResult.ok(new AdminMenuResponse(service.updateMenu(requiredUser(principal), required(request).toDomain())));
    }

    @GetMapping("/users/{userId}/scopes")
    public ApiResult<AdminUserResponse> userAuthorization(@PathVariable String userId, Principal principal) {
        String operator = requiredUser(principal);
        return ApiResult.ok(userResponse(operator, service.getUser(operator, userId)));
    }
    @PutMapping("/users/{userId}/scopes")
    public ApiResult<AdminUserResponse> replaceUserAuthorization(@PathVariable String userId,
            @RequestBody UserAuthorizationRequest request, Principal principal) {
        String operator = requiredUser(principal);
        UserAuthorizationRequest value = required(request);
        if (value.hasDetailedGrants()) {
            service.replaceUserAuthorization(operator, userId, value.getRoleCodes(), value.toScopes(userId));
        } else {
            service.replaceUserScopes(operator, userId, value.toScopeSelections());
        }
        return ApiResult.ok(userResponse(operator, service.getUser(operator, userId)));
    }

    @GetMapping("/roles/{roleCode}/menus")
    public ApiResult<AdminRoleResponse> roleMenus(@PathVariable String roleCode, Principal principal) {
        String operator = requiredUser(principal);
        return ApiResult.ok(roleResponse(operator, service.getRole(operator, roleCode)));
    }
    @PutMapping("/roles/{roleCode}/menus")
    public ApiResult<AdminRoleResponse> replaceRoleMenus(@PathVariable String roleCode,
            @RequestBody RoleMenusRequest request, Principal principal) {
        String operator = requiredUser(principal);
        service.replaceRoleMenus(operator, roleCode, required(request).getMenuCodes());
        return ApiResult.ok(roleResponse(operator, service.getRole(operator, roleCode)));
    }

    private AdminUserResponse userResponse(String operator, IamUser user) {
        return new AdminUserResponse(user, service.getUserAuthorization(operator, user.getUserId()));
    }

    private AdminRoleResponse roleResponse(String operator, IamRole role) {
        RoleMenuAssignment assignment = service.getRoleMenus(operator, role.getRoleCode());
        return new AdminRoleResponse(role, assignment.getMenuCodes());
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName().trim();
    }
    private static <T> T required(T request) {
        if (request == null) throw new IllegalArgumentException("请求体不能为空");
        return request;
    }
}
