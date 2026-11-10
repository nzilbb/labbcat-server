export interface Episode {
    episode_id: number;
    name: string;
    corpus: string;
    transcript_count: number;

    _saving: boolean;
    _changed: boolean;
    _editing: boolean;
}
