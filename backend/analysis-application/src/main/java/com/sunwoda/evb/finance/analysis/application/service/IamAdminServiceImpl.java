package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;
import com.sunwoda.evb.finance.analysis.application.port.AuditEventPort;
import com.sunwoda.evb.finance.analysis.domain.model.IamMenu;
import com.sunwoda.evb.finance.analysis.domain.model.DataScopeSelection;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.IamRole;
import com.sunwoda.evb.finance.analysis.domain.model.IamUser;
import com.sunwoda.evb.finance.analysis.domain.model.RoleMenuAssignment;
import com.sunwoda.evb.finance.analysis.domain.model.UserAuthorization;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.IamManagementRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** 本次新增：IAM 管理事务、ADMIN 双重鉴权、关联校验与审计实现。 */
@Service
public class IamAdminServiceImpl implements IamAdminService {
    private static final String ADMIN = "ADMIN";
    private final IamManagementRepository repository;
    private final UserScopeRepository scopeRepository;
    private final PermissionService permissionService;
    private final AuditEventPort audit;

    public IamAdminServiceImpl(IamManagementRepository repository, UserScopeRepository scopeRepository,
                               PermissionService permissionService, AuditEventPort audit) {
        this.repository = required(repository, "repository");
        this.scopeRepository = required(scopeRepository, "scopeRepository");
        this.permissionService = required(permissionService, "permissionService");
        this.audit = required(audit, "audit");
    }

    @Override @Transactional(readOnly = true)
    public List<IamUser> listUsers(String operator, boolean includeDisabled) {
        requireAdmin(operator);
        return repository.findUsers(includeDisabled);
    }

    @Override @Transactional(readOnly = true)
    public IamUser getUser(String operator, String userId) {
        requireAdmin(operator);
        String target = requiredText(userId, "用户工号");
        IamUser user = repository.findUser(target);
        if (user == null) throw new IllegalArgumentException("用户不存在: " + target);
        return user;
    }

    @Override @Transactional
    public IamUser createUser(String operator, IamUser user) {
        return createUser(operator, user, Collections.<String>emptyList(), Collections.<DataScopeSelection>emptyList());
    }

    @Override @Transactional
    public IamUser createUser(String operator, IamUser user, List<String> roleCodes,
                              List<DataScopeSelection> selections) {
        String actor = requireAdmin(operator);
        IamUser value = required(user, "user");
        List<String> roles = normalizeCodes(roleCodes, "角色编码");
        validateRoles(roles);
        repository.insertUser(value, actor);
        repository.replaceUserRoles(value.getUserId(), roles, actor);
        scopeRepository.replaceForUser(value.getUserId(), buildScopes(value.getUserId(), roles, selections,
                Collections.<UserScope>emptyList()));
        audit(actor, "IAM_USER_CREATE", "IAM_USER", value.getUserId(), null, state(value.isEnabled()),
                "{\"roleCount\":" + roles.size() + "}");
        return value;
    }

    @Override @Transactional
    public IamUser updateUser(String operator, IamUser user) {
        return updateUser(operator, user, null, null);
    }

    @Override @Transactional
    public IamUser updateUser(String operator, IamUser user, List<String> roleCodes,
                              List<DataScopeSelection> selections) {
        String actor = requireAdmin(operator);
        IamUser value = required(user, "user");
        IamUser before = repository.findUser(value.getUserId());
        if (before == null) throw new IllegalArgumentException("用户不存在: " + value.getUserId());
        List<String> roles = roleCodes == null ? repository.findRoleCodesByUser(value.getUserId())
                : normalizeCodes(roleCodes, "角色编码");
        validateRoles(roles);
        List<UserScope> oldScopes = scopeRepository.findByUser(value.getUserId());
        repository.updateUser(value, actor);
        repository.replaceUserRoles(value.getUserId(), roles, actor);
        List<DataScopeSelection> effectiveSelections = selections == null
                ? selectionsFrom(oldScopes) : selections;
        scopeRepository.replaceForUser(value.getUserId(), buildScopes(value.getUserId(), roles,
                effectiveSelections, oldScopes));
        audit(actor, "IAM_USER_UPDATE", "IAM_USER", value.getUserId(), state(before.isEnabled()),
                state(value.isEnabled()), "{}");
        return value;
    }

    @Override @Transactional(readOnly = true)
    public List<IamRole> listRoles(String operator, boolean includeDisabled) {
        requireAdmin(operator);
        return repository.findRoles(includeDisabled);
    }

    @Override @Transactional(readOnly = true)
    public IamRole getRole(String operator, String roleCode) {
        requireAdmin(operator);
        String code = requiredText(roleCode, "角色编码").toUpperCase(Locale.ROOT);
        IamRole role = repository.findRole(code);
        if (role == null) throw new IllegalArgumentException("角色不存在: " + code);
        return role;
    }

    @Override @Transactional
    public IamRole createRole(String operator, IamRole role) {
        return createRole(operator, role, Collections.<String>emptyList());
    }

    @Override @Transactional
    public IamRole createRole(String operator, IamRole role, List<String> menuCodes) {
        String actor = requireAdmin(operator);
        IamRole value = normalizeSystemRole(required(role, "role"));
        List<String> menus = validateMenus(menuCodes);
        repository.insertRole(value, actor);
        repository.replaceRoleMenus(value.getRoleCode(), menus, actor);
        audit(actor, "IAM_ROLE_CREATE", "IAM_ROLE", value.getRoleCode(), null, state(value.isEnabled()), "{}");
        return value;
    }

    @Override @Transactional
    public IamRole updateRole(String operator, IamRole role) {
        return updateRole(operator, role, null);
    }

    @Override @Transactional
    public IamRole updateRole(String operator, IamRole role, List<String> menuCodes) {
        String actor = requireAdmin(operator);
        IamRole value = normalizeSystemRole(required(role, "role"));
        IamRole before = repository.findRole(value.getRoleCode());
        if (before == null) throw new IllegalArgumentException("角色不存在: " + value.getRoleCode());
        List<String> menus = menuCodes == null ? repository.findMenuCodesByRole(value.getRoleCode())
                : validateMenus(menuCodes);
        repository.updateRole(value, actor);
        repository.replaceRoleMenus(value.getRoleCode(), menus, actor);
        audit(actor, "IAM_ROLE_UPDATE", "IAM_ROLE", value.getRoleCode(), state(before.isEnabled()),
                state(value.isEnabled()), "{}");
        return value;
    }

    @Override @Transactional(readOnly = true)
    public List<IamMenu> listMenus(String operator, boolean includeDisabled) {
        requireAdmin(operator);
        return repository.findMenus(includeDisabled);
    }

    @Override @Transactional
    public IamMenu createMenu(String operator, IamMenu menu) {
        String actor = requireAdmin(operator);
        IamMenu value = required(menu, "menu");
        validateParent(value);
        repository.insertMenu(value, actor);
        audit(actor, "IAM_MENU_CREATE", "IAM_MENU", value.getMenuCode(), null, state(value.isEnabled()), "{}");
        return value;
    }

    @Override @Transactional
    public IamMenu updateMenu(String operator, IamMenu menu) {
        String actor = requireAdmin(operator);
        IamMenu value = required(menu, "menu");
        IamMenu before = repository.findMenu(value.getMenuCode());
        if (before == null) throw new IllegalArgumentException("菜单不存在: " + value.getMenuCode());
        validateParent(value);
        repository.updateMenu(value, actor);
        audit(actor, "IAM_MENU_UPDATE", "IAM_MENU", value.getMenuCode(), state(before.isEnabled()),
                state(value.isEnabled()), "{}");
        return value;
    }

    @Override @Transactional(readOnly = true)
    public UserAuthorization getUserAuthorization(String operator, String userId) {
        requireAdmin(operator);
        String target = requiredText(userId, "用户工号");
        requireUser(target);
        return new UserAuthorization(target, repository.findRoleCodesByUser(target), scopeRepository.findByUser(target));
    }

    @Override @Transactional
    public UserAuthorization replaceUserAuthorization(String operator, String userId,
                                                      List<String> roleCodes, List<UserScope> scopes) {
        String actor = requireAdmin(operator);
        String target = requiredText(userId, "用户工号");
        requireUser(target);
        List<String> roles = normalizeCodes(roleCodes, "角色编码");
        List<UserScope> grants = scopes == null ? Collections.<UserScope>emptyList()
                : new ArrayList<UserScope>(scopes);
        validateRolesAndScopes(target, roles, grants);
        repository.replaceUserRoles(target, roles, actor);
        scopeRepository.replaceForUser(target, grants);
        audit(actor, "IAM_USER_AUTHORIZATION_REPLACE", "IAM_USER", target, null, "AUTHORIZED",
                "{\"roleCount\":" + roles.size() + ",\"scopeCount\":" + grants.size() + "}");
        return new UserAuthorization(target, repository.findRoleCodesByUser(target), scopeRepository.findByUser(target));
    }

    @Override @Transactional
    public UserAuthorization replaceUserScopes(String operator, String userId,
                                               List<DataScopeSelection> selections) {
        String actor = requireAdmin(operator);
        String target = requiredText(userId, "用户工号");
        requireUser(target);
        List<String> roles = repository.findRoleCodesByUser(target);
        if (roles.isEmpty()) throw new IllegalArgumentException("用户尚未分配角色，不能配置数据范围");
        List<UserScope> oldScopes = scopeRepository.findByUser(target);
        List<UserScope> grants = buildScopes(target, roles, selections, oldScopes);
        scopeRepository.replaceForUser(target, grants);
        audit(actor, "IAM_USER_SCOPE_REPLACE", "IAM_USER", target, null, "AUTHORIZED",
                "{\"scopeCount\":" + grants.size() + "}");
        return new UserAuthorization(target, roles, scopeRepository.findByUser(target));
    }

    @Override @Transactional(readOnly = true)
    public RoleMenuAssignment getRoleMenus(String operator, String roleCode) {
        requireAdmin(operator);
        String role = requiredText(roleCode, "角色编码").toUpperCase(Locale.ROOT);
        requireRole(role);
        return new RoleMenuAssignment(role, repository.findMenuCodesByRole(role));
    }

    @Override @Transactional
    public RoleMenuAssignment replaceRoleMenus(String operator, String roleCode, List<String> menuCodes) {
        String actor = requireAdmin(operator);
        String role = requiredText(roleCode, "角色编码").toUpperCase(Locale.ROOT);
        requireRole(role);
        List<String> menus = validateMenus(menuCodes);
        repository.replaceRoleMenus(role, menus, actor);
        audit(actor, "IAM_ROLE_MENU_REPLACE", "IAM_ROLE", role, null, "AUTHORIZED",
                "{\"menuCount\":" + menus.size() + "}");
        return new RoleMenuAssignment(role, repository.findMenuCodesByRole(role));
    }

    private String requireAdmin(String operator) {
        String actor = requiredText(operator, "认证用户");
        // 本次新增：兼容历史 user_scope 中的 ADMIN，同时支持新 user_role 主数据。
        if (!repository.hasEnabledRole(actor, ADMIN) && !permissionService.isEnabledAdmin(actor)) {
            throw new IllegalStateException("仅ADMIN可管理IAM权限中心");
        }
        return actor;
    }

    private void validateRolesAndScopes(String userId, List<String> roles, List<UserScope> scopes) {
        Set<String> roleSet = new LinkedHashSet<String>(roles);
        validateRoles(roles);
        for (UserScope scope : scopes) {
            if (scope == null) throw new IllegalArgumentException("数据权限不能为空");
            if (!userId.equals(scope.getUserId())) throw new IllegalArgumentException("数据权限用户与路径用户不一致");
            String role = scope.getRoleCode().trim().toUpperCase(Locale.ROOT);
            if (!roleSet.contains(role)) throw new IllegalArgumentException("数据权限角色未同时授权给用户: " + role);
            if (!scope.isEnabled()) throw new IllegalArgumentException("提交的数据权限必须为启用状态");
        }
    }

    private void validateRoles(List<String> roles) {
        for (String roleCode : roles) requireRole(roleCode);
    }

    private List<String> validateMenus(List<String> menuCodes) {
        List<String> menus = normalizeCodes(menuCodes, "菜单编码");
        for (String menuCode : menus) {
            IamMenu menu = repository.findMenu(menuCode);
            if (menu == null || !menu.isEnabled()) throw new IllegalArgumentException("菜单不存在或已停用: " + menuCode);
        }
        return menus;
    }

    private List<UserScope> buildScopes(String userId, List<String> roles,
                                        List<DataScopeSelection> selections, List<UserScope> oldScopes) {
        if (selections == null || selections.isEmpty()) return Collections.emptyList();
        List<UserScope> result = new ArrayList<UserScope>();
        for (DataScopeSelection selection : selections) {
            if (selection == null) throw new IllegalArgumentException("数据范围不能为空");
            List<AnalysisPerspective> perspectives = "ALL".equals(selection.getPerspective())
                    ? java.util.Arrays.asList(AnalysisPerspective.values())
                    : Collections.singletonList(AnalysisPerspective.fromCode(selection.getPerspective()));
            for (String roleCode : roles) {
                // 本次新增：ADMIN 全量读取来自角色本身，不制造会被误读为经营授权的 scope 行。
                if (ADMIN.equals(roleCode)) continue;
                for (AnalysisPerspective perspective : perspectives) {
                    for (String scopeCode : selection.getScopeCodes()) {
                        UserScope old = findScope(oldScopes, roleCode, perspective, scopeCode);
                        result.add(new UserScope(userId, roleCode, perspective, scopeCode,
                                old != null && old.isCanViewDetail(), old != null && old.isCanExport(),
                                old != null && old.isCanEditConfig(), true));
                    }
                }
            }
        }
        return result;
    }

    private UserScope findScope(List<UserScope> scopes, String roleCode,
                                AnalysisPerspective perspective, String scopeCode) {
        if (scopes == null) return null;
        for (UserScope scope : scopes) {
            if (scope.getRoleCode().equalsIgnoreCase(roleCode) && scope.getPerspective() == perspective
                    && scope.getScopeCode().equalsIgnoreCase(scopeCode)) return scope;
        }
        return null;
    }

    private List<DataScopeSelection> selectionsFrom(List<UserScope> scopes) {
        java.util.Map<String, List<String>> grouped = new java.util.LinkedHashMap<String, List<String>>();
        if (scopes != null) for (UserScope scope : scopes) {
            String perspective = scope.getPerspective().getCode();
            List<String> codes = grouped.get(perspective);
            if (codes == null) { codes = new ArrayList<String>(); grouped.put(perspective, codes); }
            String code = scope.getScopeCode().toUpperCase(Locale.ROOT);
            if (!codes.contains(code)) codes.add(code);
        }
        List<DataScopeSelection> result = new ArrayList<DataScopeSelection>();
        for (java.util.Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            result.add(new DataScopeSelection(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private void requireUser(String userId) {
        if (repository.findUser(userId) == null) throw new IllegalArgumentException("用户不存在: " + userId);
    }

    private void requireRole(String roleCode) {
        IamRole role = repository.findRole(roleCode);
        if (role == null || !role.isEnabled()) throw new IllegalArgumentException("角色不存在或已停用: " + roleCode);
    }

    private void validateParent(IamMenu menu) {
        if (menu.getParentCode() != null) {
            IamMenu parent = repository.findMenu(menu.getParentCode());
            if (parent == null || !parent.isEnabled()) throw new IllegalArgumentException("父菜单不存在或已停用: " + menu.getParentCode());
        }
    }

    private IamRole normalizeSystemRole(IamRole role) {
        return ADMIN.equals(role.getRoleCode()) && !role.isSystemRole()
                ? new IamRole(role.getRoleCode(), role.getRoleName(), role.getDescription(), true, role.isEnabled()) : role;
    }

    private void audit(String operator, String action, String objectType, String objectId,
                       String fromState, String toState, String metadata) {
        audit.append(new AuditEvent("iam-" + UUID.randomUUID().toString(), null, operator,
                "analysis-api", action, objectType, objectId, fromState, toState,
                "SUCCESS", null, metadata, LocalDateTime.now()));
    }

    private static String state(boolean enabled) { return enabled ? "ENABLED" : "DISABLED"; }
    private static List<String> normalizeCodes(List<String> values, String field) {
        if (values == null) return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            String normalized = requiredText(value, field).toUpperCase(Locale.ROOT);
            if (!result.contains(normalized)) result.add(normalized);
        }
        return result;
    }
    private static String requiredText(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + "不能为空");
        return value.trim();
    }
    private static <T> T required(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + "不能为空");
        return value;
    }
}
