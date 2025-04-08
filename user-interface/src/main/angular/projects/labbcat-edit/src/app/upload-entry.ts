import { SerializationDescriptor } from 'labbcat-common';
export class UploadEntry {
    id: string;
    transcript?: File;
    descriptor?: SerializationDescriptor;
    corpus?: string;
    episode?: string;
    transcriptType?: string;
    media: { [trackSuffix: string] : File[] };
    exists: boolean;
    status: string;
    errors: string[]
    progress: number;
    selected: boolean;
    transcriptId?: string; // probably the same as transcript.name, but could be different
    uploadId?: string;
    parameters?: any[];
    transcriptThreads?: { [transcriptId: string] : string };

    constructor(id: string) {
        this.id = id;
        this.media = {};
        this.status = "";
        this.errors = [];
        this.progress = 0;
        this.selected = false;
        this.exists = false;
    }

    addMedia(file: File, trackSuffix: string): void {
        if (!this.media[trackSuffix]) this.media[trackSuffix] = [];
        this.media[trackSuffix].push(file);
    }

    mediaExtensions(): string[] {
        const extensions = [];
        for (let trackSuffix in this.media) {
            for (let file of this.media[trackSuffix]) {
                const extension = file.name.replace(/^.*\.([^.]*)$/,"$1");
                if (extensions.indexOf(extension) < 0) {
                    extensions.push(extension);
                }
            } // next file
        } // next track
        return extensions;
    }
    mediaFileNames(): string[] {
        const names = [];
        for (let trackSuffix in this.media) {
            for (let file of this.media[trackSuffix]) {
                names.push(file.name);
            } // next file
        } // next track
        return names;
    }
}
