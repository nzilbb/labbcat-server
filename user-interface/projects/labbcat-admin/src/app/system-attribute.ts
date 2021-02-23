export interface SystemAttribute {
    attribute: string;
    type: string;
    style: string;
    label: string;
    description: string;
    options: object;
    value: string;

    _changed: boolean;
}
