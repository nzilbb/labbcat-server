export interface Category {
    class_id: string;
    category: string;
    label: string;
    description: string;
    display_order: string;

    _changed: boolean;
    _cantDelete: string;
    _deleting: boolean;
}
