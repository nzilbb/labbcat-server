export interface SerializationDescriptor {
    name: string;
    version: string;
    icon: string;
    numberOfInputs: number;
    mimeType: string;
    fileSuffixes: string[];
    minimumApiVersion: string;
}
