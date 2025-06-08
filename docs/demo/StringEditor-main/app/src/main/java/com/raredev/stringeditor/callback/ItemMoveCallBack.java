package com.raredev.stringeditor.callback;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class ItemMoveCallBack extends ItemTouchHelper.Callback {

  private final ItemMoveListener listener;

  public ItemMoveCallBack(ItemMoveListener listener) {
    this.listener = listener;
  }

  @Override
  public boolean isLongPressDragEnabled() {
    return false;
  }

  @Override
  public boolean isItemViewSwipeEnabled() {
    return false;
  }

  @Override
  public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
    int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
    return makeMovementFlags(dragFlags, 0);
  }

  @Override
  public boolean onMove(
      RecyclerView recyclerView,
      RecyclerView.ViewHolder viewHolder,
      RecyclerView.ViewHolder target) {
    listener.onItemMove(viewHolder, viewHolder.getAdapterPosition(), target.getAdapterPosition());
    return true;
  }

  @Override
  public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
  }

  @Override
  public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
    super.onSelectedChanged(viewHolder, actionState);
    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
      // view está sendo arrastada
    } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
      // view não está mais sendo arrastada
      listener.onDragFinish();
    }
  }

  public interface ItemMoveListener {
    boolean onItemMove(RecyclerView.ViewHolder viewHolder, int fromPosition, int toPosition);
    void onDragFinish();
  }
}
