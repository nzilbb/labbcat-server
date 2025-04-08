export interface SerializationDescriptor {
    name: string
    mimeType: string;
    fileSuffixes: string[];
    icon: string; // URL
    numberOfInputs: number;
    version: string;
}
