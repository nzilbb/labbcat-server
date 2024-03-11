import { MatrixLayerMatch } from './matrix-layer-match';

/** One column in a search matrix, containing patterns matching one word token. */
export interface MatrixColumn {
    /**
     * The layer patterns that match a word token. Keys are layer IDs, values are arrays of
     * MatrixLayerMatch.
     * Generally there will be one {@link LayerMatch} per layer in a column, but for
     * word-internal segment context matching, there may be more than one, corresponding to
     * multiple sequential segments within the word.
     */
    layers: object; /* key=layerId, value=MatrixLayerMatch[]*/

    /** Adjecency; how far matches of the following column in the matrix can be from matches
     * of this column. 1 means it must immediately follow, 2 means there can be one
     * intervening token, etc. */
    adj: number;
}
