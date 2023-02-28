export interface Annotation {
    id: string;
    layerId: string;
    label: string;
    startId: string;
    endId: string;
    ordinal: number;
    annotations: object;
    
    _changed: boolean;
}
