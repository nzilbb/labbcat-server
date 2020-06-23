export interface Corpus {
    corpus_id: string;
    corpus_name: string;
    corpus_language: string;
    corpus_description: string;

    _changed: boolean;
    _cantDelete: string;
}
