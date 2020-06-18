export interface Response {
    version: string;
    code: number;
    errors: string[];
    messages: string[];
    model: any;
}
