export interface Layer {
    id: string;
    parentId: string;
    description: string;
    alignment: number;
    peers: boolean;
    peersOverlap: boolean;
    parentIncludes: boolean;
    saturated: boolean;
    type: string;
    category: string;
    validLabels: object;

    layer_manager_id: string; // TODO - remove this once new automation API is live
    enabled: string; // TODO - remove this once new automation API is live

    _selected: boolean;
    _changed: boolean;
    _cantDelete: string;
    _deleting: boolean;
}
