import { SerializationDescriptor } from 'labbcat-common';
export class UploadEntry {
    id: string;
    transcript?: File;
    descriptor?: SerializationDescriptor;
    corpus?: string;
    episode?: string;
    transcriptType?: string;
    media: { [trackSuffix: string] : File[] };
    exists?: boolean;
    status: string;
    errors: string[]
    progress: number;
    selected: boolean;
    transcriptId?: string; // probably the same as transcript.name, but could be different
    uploadId?: string;
    parameters?: any[];
    parametersVisible: boolean;
    transcriptThreads?: { [transcriptId: string] : string };

    constructor(id: string) {
        this.id = id;
        this.media = {};
        this.status = "";
        this.errors = [];
        this.progress = 0;
        this.selected = false;
        this.exists = null; // don't know yet
    }

    resetState() {
        this.status = "";
        this.errors = [];
        this.progress = 0;
        this.selected = false;
        this.uploadId = null;
        this.parameters = null;
        this.parametersVisible = false;
        this.transcriptThreads = null;
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
    // true if there are server-side threads processing the upload, false otherwise
    generating(): boolean {
        return this.transcriptThreads && Object.keys(this.transcriptThreads).length > 0;
    }
}
