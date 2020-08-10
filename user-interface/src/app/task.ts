export interface Task {
    threadId: string;
    threadName: string;
    resultUrl: string;
    resultText: string;
    running: boolean;
    duration: number;
    percentComplete: number;
    status: string;
    refreshSeconds: number;
}
