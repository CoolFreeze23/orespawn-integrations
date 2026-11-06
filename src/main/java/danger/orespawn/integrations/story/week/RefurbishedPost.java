package danger.orespawn.integrations.story.week;

import com.mrcrayfish.furniture.refurbished.mail.DeliveryResult;
import com.mrcrayfish.furniture.refurbished.mail.DeliveryService;
import com.mrcrayfish.furniture.refurbished.mail.IMailbox;
import com.mrcrayfish.furniture.refurbished.mail.Mailbox;
import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;

import java.util.UUID;

/**
 * The Gazette's first route: the first Refurbished Furniture mailbox the subscriber owns, in the order
 * {@code DeliveryService.getMailboxes()} lists them, sent with {@code sendMail} (checked against
 * refurbished_furniture-neoforge-1.21.1-1.0.22: the owner is the {@code Mailbox} record's {@code owner()} UUID, and
 * mail waits in its saved {@code queue()} until the box's chunk loads). An unread copy still queued or still in a loaded
 * box is taken out first, so the paper never piles up.
 */
public final class RefurbishedPost implements Gazette.Mailman {

    private RefurbishedPost() {
    }

    public static void init(IEventBus modBus) {
        Gazette.refurbished = new RefurbishedPost();
    }

    @Override
    public Gazette.Delivery deliver(MinecraftServer server, UUID player, ItemStack copy) {
        try {
            DeliveryService service = DeliveryService.get(server).orElse(null);
            if (service == null) {
                return null;
            }
            for (IMailbox entry : service.getMailboxes()) {
                if (!(entry instanceof Mailbox box) || box.removed().booleanValue()
                        || !player.equals(box.owner().getValue())) {
                    continue;
                }
                box.queue().removeIf(GazetteItems::unread);
                ServerLevel level = server.getLevel(box.levelKey());
                if (level != null) {
                    Gazette.clearUnread(level, box.pos());
                }
                DeliveryResult result = service.sendMail(box.id(), copy);
                if (result.success()) {
                    return new Gazette.Delivery(box.levelKey(), box.pos());
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("gazette.refurbished", t);
        }
        return null;
    }
}
