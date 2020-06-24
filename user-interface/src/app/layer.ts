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
}
