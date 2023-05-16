package net.mehvahdjukaar.supplementaries.integration.forge.create;

import com.simibubi.create.content.logistics.block.display.DisplayLinkContext;
import com.simibubi.create.content.logistics.block.display.target.DisplayTarget;
import com.simibubi.create.content.logistics.block.display.target.DisplayTargetStats;
import com.simibubi.create.foundation.utility.Lang;
import net.mehvahdjukaar.supplementaries.common.block.tiles.NoticeBoardBlockTile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class NoticeBoardDisplayTarget extends DisplayTarget {

    @Override
    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        BlockEntity te = context.getTargetTE();
        if (te instanceof NoticeBoardBlockTile lectern) {
            ItemStack book = lectern.getDisplayedItem();
            if (!book.isEmpty()) {
                if (book.is(Items.WRITABLE_BOOK)) {
                    lectern.setDisplayedItem(book = this.signBook(book));
                }

                if (book.is(Items.WRITTEN_BOOK)) {
                    ListTag tag = book.getTag().getList("pages", 8);
                    boolean changed = false;

                    for (int i = 0; i - line < text.size() && i < 50; ++i) {
                        if (tag.size() <= i) {
                            tag.add(StringTag.valueOf(i < line ? "" : Component.Serializer.toJson(text.get(i - line))));
                        } else if (i >= line) {
                            if (i - line == 0) {
                                reserve(i, lectern, context);
                            }

                            if (i - line > 0 && this.isReserved(i - line, lectern, context)) {
                                break;
                            }

                            tag.set(i, StringTag.valueOf(Component.Serializer.toJson( text.get(i - line))));
                        }

                        changed = true;
                    }

                    book.getTag().put("pages", tag);
                    lectern.setDisplayedItem(book);
                    if (changed) {
                        context.level().sendBlockUpdated(context.getTargetPos(), lectern.getBlockState(), lectern.getBlockState(), 2);
                    }
                }
            }
        }
    }

    @Override
    public DisplayTargetStats provideStats(DisplayLinkContext context) {
        return new DisplayTargetStats(50, 256, this);
    }

    @Override
    public Component getLineOptionText(int line) {
        return Lang.translateDirect("display_target.page", line + 1);
    }

    private ItemStack signBook(ItemStack book) {
        ItemStack written = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag compoundtag = book.getTag();
        if (compoundtag != null) {
            written.setTag(compoundtag.copy());
        }

        written.addTagElement("author", StringTag.valueOf("Data Gatherer"));
        written.addTagElement("filtered_title", StringTag.valueOf("Printed Book"));
        written.addTagElement("title", StringTag.valueOf("Printed Book"));
        return written;
    }
}
