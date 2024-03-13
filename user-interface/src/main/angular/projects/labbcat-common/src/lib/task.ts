export interface Task {
    threadId: string;
    threadName: string;
    resultUrl: string;
    resultText: string;
    seriesId: string;
    running: boolean;
    duration: number;
    percentComplete: number;
    status: string;
    refreshSeconds: number;
    lastException: string;
    stackTrace: string;
}
