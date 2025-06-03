package com.raredev.stringeditor.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.blankj.utilcode.util.ToastUtils;
import com.raredev.stringeditor.adapter.ElementsAdapter;
import com.raredev.stringeditor.callback.DialogCallback;
import com.raredev.stringeditor.callback.ItemMoveCallBack;
import com.raredev.stringeditor.databinding.FragmentElementListBinding;
import com.raredev.stringeditor.dialog.AttributeDialog;
import com.raredev.stringeditor.model.*;
import com.raredev.stringeditor.utils.XmlGeneratorUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
public class ElementListFragment extends Fragment implements ItemMoveCallBack.ItemMoveListener {
  private FragmentElementListBinding binding;

  private List<BaseElement> listElements = new ArrayList<>();
  private ElementsAdapter adapter;

  private DataObserver observer;

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    binding = FragmentElementListBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    init();
  }

  @Override
  public void onStart() {
    super.onStart();
    getActivity().invalidateOptionsMenu();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    adapter.unregisterAdapterDataObserver(observer);
    binding = null;
  }
  
  @Override
  public boolean onItemMove(RecyclerView.ViewHolder holder, int fromPosition, int toPosition) {
    Collections.swap(listElements, fromPosition, toPosition);
    adapter.notifyItemMoved(fromPosition, toPosition);
    return true;
  }

  @Override
  public void onDragFinish() {
    adapter.notifyDataSetChanged();
  }

  private void init() {
    adapter = new ElementsAdapter(listElements);
    observer = new DataObserver();

    ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemMoveCallBack(this));
    touchHelper.attachToRecyclerView(binding.rvElements);
    
    adapter.registerAdapterDataObserver(observer);
    adapter.setTouchHelper(touchHelper);

    binding.rvElements.setLayoutManager(new LinearLayoutManager(getContext()));
    binding.rvElements.setAdapter(adapter);

    binding.fab.setOnClickListener((v) -> dialogNewElement());

    adapter.setElementListener(
        new ElementsAdapter.ElementListener() {
          @Override
          public void onElementClick(View v, int pos) {}

          @Override
          public void onElementLongClick(View v, int pos) {
            showPopupMenu(v, pos);
          }
        });

    binding.rvElements.addOnScrollListener(
        new RecyclerView.OnScrollListener() {
          @Override
          public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            if (dy < 0) {
              // Scrolling up
              binding.fab.extend();
            } else {
              // Scrolling down
              binding.fab.shrink();
            }
          }
        });

    listElements.addAll(XmlGeneratorUtils.getInstance().xmlToList());
    adapter.notifyDataSetChanged();
  }

  private void showPopupMenu(View v, int pos) {
    PopupMenu menu = new PopupMenu(requireActivity(), v);
    menu.getMenu().add(0, 0, 0, "Edit");
    menu.getMenu().add(0, 1, 1, "Remove");
    menu.setOnMenuItemClickListener(
        (item) -> {
          final var title = item.getTitle();
          if (title == "Edit") {
            dialogEditElement(pos);
          } else if (title == "Remove") {
            listElements.remove(pos);
            ToastUtils.showShort("Removed");
            adapter.notifyDataSetChanged();
          }
          return true;
        });
    menu.show();
  }

  private void dialogEditElement(int pos) {
    AttributeDialog dialog = new AttributeDialog(requireActivity(),
      new DialogCallback() {
        @Override
        public void onPositiveButtonClicked(String name, String value) {
          listElements.set(pos, BaseElement.newString(name, value));
          adapter.notifyDataSetChanged();
        }
    });
    dialog.getEditTextName().setText(listElements.get(pos).getName());
    dialog.getEditTextValue().setText((String)listElements.get(pos).getValue());
    dialog.setTitle("Edit Element");
    dialog.setTextWatcher();
    dialog.show();
  }

  private void dialogNewElement() {
    AttributeDialog dialog = new AttributeDialog(requireActivity(),
      new DialogCallback() {
        @Override
        public void onPositiveButtonClicked(String name, String value) {
          listElements.add(BaseElement.newString(name, value));
          adapter.notifyDataSetChanged();
        }
    });
    dialog.setTitle("New Element");
    dialog.setTextWatcher();
    dialog.show();
  }

  class DataObserver extends RecyclerView.AdapterDataObserver {
    @Override
    public void onChanged() {
      super.onChanged();
      XmlGeneratorUtils.getInstance().generateXml(listElements);
    }
  }

  public static ElementListFragment newInstance() {
    ElementListFragment fragment = new ElementListFragment();
    fragment.setRetainInstance(true);
    return fragment;
  }
}