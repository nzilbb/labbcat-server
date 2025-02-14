export interface Anchor {
    id: string;
    offset: number;
    startOf: object; // keys are layerId, values are arrays of Annotations
    endOf: object; // keys are layerId, values are arrays of Annotations
}
