export interface DashboardItem {
    item_id: number;           // ID of the item.
    type: "link"|"sql"|"exec"; // The type of the item.
    label: string;             // The item's text label.
    icon: string;              // The item's icon.
    definition: string;        // The item's link/query.
    dashboard: number;         // whether to display on home page
    express: number;           // whether to display on 'express' home page
    statistics: number;        // whether to display on statistics page
    display_order: number;     // list ordering value
    value: string;             // The item's value.
    error: string;             // The error generated when evaluating the item, if any

    _changed: boolean;
    _deleting: boolean;
}
