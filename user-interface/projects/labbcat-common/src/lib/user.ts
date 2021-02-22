export interface User {
    user: string;
    email: string;
    resetPassword: boolean;
    roles: string[];
    
    _cantDelete: string;
    _changed: boolean;
    _deleting: boolean;
}
