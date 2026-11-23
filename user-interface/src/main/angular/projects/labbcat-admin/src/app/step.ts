/** Elicitation task step */
export interface Step {
    task_id: number;
    step_id: number;
    display_order: number;
    title: string;
    prompt: string;
    transcript: string;
    shuffle_words: 0 | 1;
    record: 0 /*nothing*/ | 1 /* speech */ | 2 /* attribute value */; 
    max_seconds: number;
    suppress_next: 0 | 1;
    next_delay_seconds: number;
    suppress_rerecord: 0 | 1 | -1;
    image: string;
    countdown_seconds: number;
    group_id: number;
    tags: string;
    sample: string;
    step_count: number;
    attribute: string;
    condition_attribute: string;
    condition_value: string;
    validation_javascript: string;
    prompt_before_recording: string;
    prompt_during_recording: string;
    prompt_after_recording: string;
    options: object[];
    type: string;
    style: string;
    steps: Step[];
}
