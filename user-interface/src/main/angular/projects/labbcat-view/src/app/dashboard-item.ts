export interface DashboardItem {
    item_id: number;           // ID of the item.
    type: "link"|"sql"|"exec"; // The type of the item.
    label: string              // The item's text label.
    icon: string               // The item's icon.
    value: string              // The item's value.
    error: string              // The error generated when evaluating the item, if any
}
