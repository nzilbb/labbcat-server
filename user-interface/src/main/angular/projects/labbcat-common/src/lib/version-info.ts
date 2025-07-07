/** Component versions information returned by the server. */
export interface VersionInfo {
    System: { [id: string]: string; }
    Formats: { [id: string]: string; }
    Annotators: { [id: string]: string; }
    ThirdPartySoftware: { [id: string]: string; }
    RDBMS: { [id: string]: string; }
    LayerManagers: { [id: string]: string; }
}
