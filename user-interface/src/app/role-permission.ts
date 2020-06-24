export interface RolePermission {
    role_id: string;
    entity: string;
    attribute_name: string;
    value_pattern: string;

    _changed: boolean;
    _cantDelete: string;
}
