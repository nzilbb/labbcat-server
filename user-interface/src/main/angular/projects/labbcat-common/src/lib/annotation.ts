export interface Annotation {
    id: string;
    layerId: string;
    label: string;
    startId: string;
    endId: string;
    parentId: string;
    ordinal: number;

    // attributes that may be supplied by nzilbb.ag.Annotation
    layer: any;
    previous: any;
    next: any;
    start: any;
    end: any;

    // property that may be present for annotations individually via API
    annotations: any;

    // methods that may be supplied by nzilbb.ag.Annotation
    includesOffset : (offset: number) => boolean;
    includes : (annotation: Annotation) => boolean;
    includesMidpoint : (annotation: Annotation) => boolean;
    overlaps : (annotation: Annotation) => boolean;
    duration : () => number;
    midpoint : () => number;
    instantaneous : () => boolean;
    toString :  () => string;
    sharesStart : (layerId: string) => boolean;
    sharesEnd : (layerId: string) => boolean;
    startsWith : (annotation: Annotation) => boolean;
    endsWith : (annotation: Annotation) => boolean;
    tags : (annotation: Annotation) => boolean;
    predecessorOf : (annotation: Annotation) => boolean;
    successorOf : (annotation: Annotation) => boolean;
    tagOn : (layerId: string) => boolean;
    first : (layerId: string) => Annotation;
    last : (layerId: string) => Annotation;
    all : (layerId: string) => Annotation[];
    
    _changed: boolean;
}
