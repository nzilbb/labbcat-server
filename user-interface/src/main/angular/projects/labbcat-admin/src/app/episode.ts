export interface Episode {
    episode_id: number;
    name: string;
    corpus: string;
    transcript_count: number;

    _changed: boolean;
    _editing: boolean;
}
