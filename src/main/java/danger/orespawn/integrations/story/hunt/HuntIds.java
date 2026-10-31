package danger.orespawn.integrations.story.hunt;

/**
 * The Royal Hunt's CustomNPCs ids, in one plain class so the handlers that open her dialogs never load a CustomNPCs
 * class: Captain Iracema's clone in OI's tab 9 and her dialogs (730 on, beside the Registry's 710-723 and the
 * shipped 701).
 */
public final class HuntIds {

    public static final int CLONE_TAB = 9;
    public static final String CLONE_IRACEMA = "iracema";
    /** Her greeting (the clone's own dialog): the board, Work for the Hunt, "And the King?", the Mobzilla hunt. */
    public static final int DIALOG_GREETING = 730;
    /** The Monday board; tents and Registry desks open this one directly. */
    public static final int DIALOG_BOARD = 731;
    /** Work for the Hunt: 701, 702 and 703 by availability. */
    public static final int DIALOG_WORK = 732;
    /** The three quest offers behind Work for the Hunt, each available only while its quest is unfinished. */
    public static final int DIALOG_OFFER_701 = 741;
    public static final int DIALOG_OFFER_702 = 742;
    public static final int DIALOG_OFFER_703 = 743;
    /** The shipped Mobzilla dialog, re-keyed. */
    public static final int DIALOG_MOBZILLA = 701;
    /** The marker tag the Lodge template carries where she stands. */
    public static final String MARKER_TAG = "cc_iracema";

    private HuntIds() {
    }
}
