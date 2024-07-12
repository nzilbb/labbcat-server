import { ValidLabelDefinition } from './valid-label-definition';

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
    subtype: string;
    category: string;
    validLabels: object;

    validLabelsDefinition: ValidLabelDefinition[];

    // temporal layer
    layer_id: string; // TODO - remove this once new automation API is live
    layer_manager_id: string; // TODO - remove this once new automation API is live
    extra: string; // TODO - remove this once new automation API is live
    enabled: string; // TODO - remove this once new automation API is live

    // transcript/participant attribute:
    class_id: string;
    attribute: string;
    hint: string;
    searchable: string;
    access: string;
    display_order: number;
    style: string;
    // style format depends on subtype
    // select: "[other] [multiple] [radio]"
    other: boolean;
    multiple: boolean;
    radio: boolean;
    // string: "${size}"
    size: number;
    // number/integer: "${size} ${min}-${max} ${minLabel}|${maxLabel} [slider]"
    min: number;
    max: number;
    minLabel: string;
    maxLabel: string;
    slider: boolean;
    // date/datetime: " ${min}-${max}" or "${min} ${max} "
    // text: "${cols}x${rows}"
    cols: number;
    rows: number;

    _selected: boolean;
    _changed: boolean;
    _cantDelete: string;
    _deleting: boolean;
}
