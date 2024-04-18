export interface MediaFile {
    trackSuffix: string; // The track suffix of the media.
    mimeType: string; // The MIME type of the file.
    type: string; // The inferred media type of the file.
    url: string; // URL to the content of the file.
    name: string; // Name of the file
    generateFrom: MediaFile // The media file from which this one could be generated, or null if the file already exists.
}
