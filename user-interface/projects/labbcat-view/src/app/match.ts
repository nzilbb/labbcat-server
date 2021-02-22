export interface Match {
    MatchId: string;
    Transcript: string;
    Participant: string;
    Corpus: string;
    Line: number;
    LineEnd: number;
    BeforeMatch: string;
    Text: string;
    AfterMatch: string;

    _selected: boolean;
}

