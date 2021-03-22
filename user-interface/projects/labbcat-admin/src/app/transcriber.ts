export interface Transcriber {
    transcriberId: string;
    version: string;
    hasConfigWebapp: boolean;
    minimumApiVersion: string;
    info: string;
    installedVersion: string;
    jar: string;

    _showInfo: boolean;
    _deleting: boolean;
}
