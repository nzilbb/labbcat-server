/** The definition of a valid label within the hierarchical label picker */
export interface ValidLabelDefinition {
    // what the underlying label is in LaBB-CAT (i.e. the DISC label, for a DISC layer)
    label: string; 
    // the symbol in the transcript, for the label (e.g. the IPA version of the label)
    display: string;
    // the symbol on the label helper, for the label (e.g. the IPA version of the label)
    // - if there's no selector specified, then the value for display is used,
    // and if there's no value for display specified, then there's no option
    // on the label helper (so that type-able consonants like p, b, t, d etc. don't
    // take up space on the label helper)
    selector: string;
    // tool-tip text that appears if you hover the mouse over the IPA symbol in the helper
    description: string;
    // the broad category of the symbol, for organizing the layout of the helper
    category: string;
    // the narrower category of the symbol, for listing subgroups of symbols together
    subcategory: string;
    // the order to process/list the labels in
    display_order: number;
}
