export interface RolePermission {
    role_id: string;
    entity: string;
    layer: string;
    value_pattern: string;

    _changed: boolean;
    _cantDelete: string;
    _deleting: boolean;
}
