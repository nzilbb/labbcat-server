export interface ProgressUpdate {
    message: string;
    value: number;
    maximum: number;
    link?: string;
    linkTitle?: string;
    error?: string;
    code?: string;
}
