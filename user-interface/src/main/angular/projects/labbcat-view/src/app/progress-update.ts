export interface ProgressUpdate {
    message: string;
    value: number;
    maximum: number;
    error?: string;
    code?: string;
}
