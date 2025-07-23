export interface Task {
    threadId: string;
    threadName: string;
    who: string;
    creationTime: string;
    resultUrl?: string;
    resultText?: string;
    running: boolean;
    duration: number;
    percentComplete: number;
    status: string;
    refreshSeconds: number;
    lastException?: string;
    stackTrace?: string;
    // may be present for search threads:
    size: number;
    seriesId?: string;
    layers?: string[];
    targetLayer?: string; 
    csv?: string;
    csvColumns?: string[];
}
