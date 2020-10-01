export interface Annotator {
    annotatorId: string;
    version: string;
    hasConfigWebapp: boolean;
    hasTaskWebapp: boolean;
    hasExtWebapp: boolean;
    minimumApiVersion: string;
    info: string;
    installedVersion: string;
    jar: string;

    _showInfo: boolean;
    _deleting: boolean;
}
