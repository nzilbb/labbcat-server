export interface Project {
    project_id: string;
    project: string;
    description: string;

    _changed: boolean;
    _cantDelete: string;
    _deleting: boolean;
}
