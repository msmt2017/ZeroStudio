package com.raredev.stringeditor.adapter;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.raredev.stringeditor.databinding.ElementItemBinding;
import com.raredev.stringeditor.model.BaseElement;
import java.util.List;

public class ElementsAdapter extends RecyclerView.Adapter<ElementsAdapter.VH> {
  private List<BaseElement> listElements;
  
  private ItemTouchHelper touchHelper;
  private ElementListener listener;
  
  public ElementsAdapter(List<BaseElement> listElements) {
    this.listElements = listElements;
  }

  @Override
  public VH onCreateViewHolder(ViewGroup parent, int position) {
    return new VH(ElementItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
  }

  @Override
  public void onBindViewHolder(VH holder, int position) {
    BaseElement element = listElements.get(position);

    holder.tvName.setText(element.getName());
    if (element.getValue() instanceof String) {
      holder.tvValue.setText((String)element.getValue());
    }

    holder.itemView.setOnClickListener((v) -> listener.onElementClick(v, position));
    holder.itemView.setOnLongClickListener(
        (v) -> {
          listener.onElementLongClick(v, position);
          return true;
        });

    holder.imgDrag.setOnTouchListener(
        (v, event) -> {
          if (event.getActionMasked() == MotionEvent.ACTION_DOWN) touchHelper.startDrag(holder);
          return false;
        });
  }

  public void setTouchHelper(ItemTouchHelper touchHelper) {
    this.touchHelper = touchHelper;
  }

  public void setElementListener(ElementListener listener) {
    this.listener = listener;
  }

  public interface ElementListener {
    void onElementClick(View v, int pos);
    void onElementLongClick(View v, int pos);
  }

  @Override
  public int getItemCount() {
    return listElements.size();
  }

  public class VH extends RecyclerView.ViewHolder {
    public ElementItemBinding binding;
    public TextView tvName, tvValue;
    public ImageView imgDrag;

    public VH(@NonNull ElementItemBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
      tvName = binding.name;
      tvValue = binding.value;
      imgDrag = binding.iconDrag;
    }
  }
}
