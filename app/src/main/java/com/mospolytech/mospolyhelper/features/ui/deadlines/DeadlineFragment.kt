package com.mospolytech.mospolyhelper.features.ui.deadlines

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.mospolytech.mospolyhelper.NavGraphDirections
import com.mospolytech.mospolyhelper.features.ui.main.MainActivity
import com.mospolytech.mospolyhelper.R
import com.mospolytech.mospolyhelper.domain.deadline.model.Deadline
import com.mospolytech.mospolyhelper.features.ui.deadlines.bottomdialog.AddBottomSheetDialogFragment
import com.mospolytech.mospolyhelper.utils.TAG
import com.mospolytech.mospolyhelper.utils.safe
import kotlinx.android.synthetic.main.fragment_deadline.*
import kotlinx.android.synthetic.main.toolbar_deadline.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.math.roundToInt
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.coroutines.CoroutineContext


class DeadlineFragment : Fragment(), CoroutineScope,
    View.OnClickListener {


    private lateinit var mainActivity: MainActivity
    private lateinit var bot: AddBottomSheetDialogFragment
    private lateinit var fm: FragmentManager
    private lateinit var vibrator: Vibrator

    private var isVibrated = false
    private val viewModel by viewModel<DeadlineViewModel>()

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job


    enum class DataType {
        FULL, NOTCOMP, FIND
    }

    private lateinit var type: DataType
    companion object {
        fun newInstance() =
            DeadlineFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_deadline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity = activity as MainActivity
        bot = AddBottomSheetDialogFragment.newInstance(requireContext())
        fm = (activity as MainActivity).supportFragmentManager
        vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        setRecycler()
        setToolbar()
        fab.setOnClickListener(this)
    }

    // простите меня за это
    private fun temporaryFix() {
        bot.show(fm,
            AddBottomSheetDialogFragment.TAG
        )
        bot.dismiss()
        bot.show(fm,
            AddBottomSheetDialogFragment.TAG
        )
        bot.dismiss()
    }

    override fun onResume() {
        super.onResume()
        temporaryFix()
        defaultData()
        editDeadline()
        deleteDeadline()
        receiveName()
    }

    override fun onPause() {
        super.onPause()
        viewModel.nameReceiver.removeObservers(viewLifecycleOwner)
        viewModel.edit.removeObservers(viewLifecycleOwner)
        viewModel.foundData.removeObservers(viewLifecycleOwner)
        viewModel.delete.removeObservers(viewLifecycleOwner)
        bot.dismiss()
    }

    private fun receiveName() {
        viewModel.nameReceiver.observe(viewLifecycleOwner, Observer<String> {
            bot.setName(it)
            bot.show(fm,
                AddBottomSheetDialogFragment.TAG
            )
        })
    }

    private fun editDeadline() {
        viewModel.edit.observe(viewLifecycleOwner, Observer<Deadline> {
            if (!viewModel.isUpdated(it))
            {
                bot.setEdit(it)
                bot.show(fm,
                    AddBottomSheetDialogFragment.TAG
                )
            }
        })
    }

    private fun deleteDeadline() {
        viewModel.delete.observe(viewLifecycleOwner, Observer<Deadline> {
            if (!viewModel.isDeleted(it))
            {
                viewModel.deleteOne(it)
                val snackbar = Snackbar.make(requireView(),
                    R.string.deleteDeadline, Snackbar.LENGTH_SHORT)
                var isRemoved = true
                snackbar
                    .setAction(R.string.cancel) {
                        isRemoved = false
                    }
                    .addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            if (!isRemoved) {
                                viewModel.saveInformation(it)
                            }
                        }
                    })
                    .show()
            }
        })
    }

    private fun defaultData() {
        loading_spinner.visibility = View.VISIBLE
        type = DataType.FULL
        viewModel.data.observe(viewLifecycleOwner, Observer<List<Deadline>> {
            if (recycler.adapter == null) {
                recycler.adapter =
                    MyDeadlineRecyclerViewAdapter(
                        it,
                        viewModel
                    )
            } else {
                (recycler.adapter as MyDeadlineRecyclerViewAdapter).updateBookList(it)
            }
            loading_spinner.visibility = View.GONE
            noDeadlines((recycler.adapter as MyDeadlineRecyclerViewAdapter).itemCount != 0)
        })
    }

    private fun requestData(typeData: DataType) {
        unObserve()
        when(typeData){
            DataType.FULL -> {
                viewModel.data.observe(viewLifecycleOwner, Observer<List<Deadline>> {
                    if (recycler.adapter is MyDeadlineRecyclerViewAdapter) {
                        (recycler.adapter as MyDeadlineRecyclerViewAdapter).updateBookList(it)
                    } else {
                        recycler.adapter =
                            MyDeadlineRecyclerViewAdapter(
                                it,
                                viewModel
                            )
                    }
                    noDeadlines((recycler.adapter as MyDeadlineRecyclerViewAdapter).itemCount != 0)
                })
            }
            DataType.FIND -> {
                viewModel.foundData.observe(viewLifecycleOwner, Observer<List<Deadline>> {
                    if (recycler.adapter is MyDeadlineRecyclerViewAdapter) {
                        (recycler.adapter as MyDeadlineRecyclerViewAdapter).updateBookList(it)
                    } else {
                        recycler.adapter =
                            MyDeadlineRecyclerViewAdapter(
                                it,
                                viewModel
                            )
                    }
                    noDeadlines((recycler.adapter as MyDeadlineRecyclerViewAdapter).itemCount != 0)
                })
            }
            DataType.NOTCOMP -> {
                viewModel.dataCurrent.observe(viewLifecycleOwner, Observer<List<Deadline>> {
                    (recycler.adapter as MyDeadlineRecyclerViewAdapter).updateBookList(it)
                    noDeadlines((recycler.adapter as MyDeadlineRecyclerViewAdapter).itemCount != 0)
                })
            }
        }
        type = typeData
    }

    private fun setRecycler() {
        recycler.layoutManager = LinearLayoutManager(context)
        registerForContextMenu(recycler)
        val itemTouchHelper = ItemTouchHelper(simpleItemTouchCallback)
        itemTouchHelper.attachToRecyclerView(recycler)
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0) {
                    fab.hide()
                }
                else if (dy < 0) {
                    if (toolbar_deadline_main.visibility != View.GONE) {
                        fab.show()
                    }
                }
            }
        })
    }

    private fun noDeadlines(t: Boolean) {
        textViewEmpty.visibility = if (t) View.GONE else View.VISIBLE
    }

    private var simpleItemTouchCallback: ItemTouchHelper.SimpleCallback = object :
        ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

        override fun onMove(recyclerView: RecyclerView,
                            viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
        ): Boolean {
            if (viewHolder is DeadlinesViewHolder) {
                viewHolder.closeContextMenu()
            }
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int) {
            if (swipeDir == ItemTouchHelper.LEFT) {
                if (viewHolder is DeadlinesViewHolder) {
                    viewHolder.closeContextMenu()
                    viewModel.delete(viewHolder.getDeadline())
                }
            }
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            val trashBinIcon = resources.getDrawable(
                R.drawable.ic_delete_black_24dp,
                (activity as MainActivity).theme
            )
            //c.clipRect(viewHolder.itemView.width.toFloat(), viewHolder.itemView.top.toFloat(),
            //    dX, viewHolder.itemView.bottom.toFloat())
            val textMargin = resources.getDimension(R.dimen.icon_margin)
                .roundToInt()
            trashBinIcon.bounds = Rect(
                viewHolder.itemView.width - trashBinIcon.intrinsicWidth + textMargin + dX.toInt(),
                viewHolder.itemView.top +
                        (viewHolder.itemView.height - trashBinIcon.intrinsicHeight) / 2,
                viewHolder.itemView.width + textMargin + dX.toInt(),
                viewHolder.itemView.top +
                        (viewHolder.itemView.height + trashBinIcon.intrinsicHeight) / 2
            )
            if (-dX.toInt() >= viewHolder.itemView.width / 2 && isVibrated) {
                isVibrated = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            20,
                            VibrationEffect.EFFECT_TICK
                        ))
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    vibrator.vibrate(20)
                    // api 23??
                }
            }
            if (-dX.toInt() < viewHolder.itemView.width / 2) {
                isVibrated = true
            }
            if (-dX.toInt() != viewHolder.itemView.width) {
                val paint = Paint()
                paint.style = Paint.Style.FILL_AND_STROKE
                paint.color = Color.RED
                c.drawCircle(
                    viewHolder.itemView.width - trashBinIcon.intrinsicWidth/2 + textMargin + dX,
                    (viewHolder.itemView.top + viewHolder.itemView.height / 2).toFloat(),
                    -dX/5,
                    paint
                )
                trashBinIcon.draw(c)
            }
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    override fun onClick(v: View?) {
        bot.show(fm,
            AddBottomSheetDialogFragment.TAG
        )
    }

    private fun unObserve() {
        when (type) {
            DataType.FULL ->  viewModel.clearObserveData(viewLifecycleOwner)
            DataType.FIND -> viewModel.clearObserveFind(viewLifecycleOwner)
            DataType.NOTCOMP -> viewModel.clearObserveDataCur(viewLifecycleOwner)
        }
    }

    private fun setToolbar(){
        val bottomAppBar = bottomAppBar
        (activity as MainActivity).setSupportActionBar(bottomAppBar)
        (activity as MainActivity).supportActionBar!!.setDisplayShowTitleEnabled(false)
        val inputMethodManager =
            requireActivity().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        button_search_deadline.setOnClickListener {
            toolbar_deadline_main.visibility = View.GONE
            toolbar_deadline_search.visibility = View.VISIBLE
            edit_search_deadline.requestFocus()
            inputMethodManager.showSoftInput(edit_search_deadline, 0)
            if (!viewModel.foundData.hasActiveObservers()) {
                requestData(DataType.FIND)
            } else if (it.toString().isEmpty()) {
                requestData(DataType.FULL)
            }
            fab.hide()
        }
        button_search_clear.setOnClickListener {
            toolbar_deadline_main.visibility = View.VISIBLE
            toolbar_deadline_search.visibility = View.GONE
            edit_search_deadline.text.clear()
            inputMethodManager.hideSoftInputFromWindow(requireView().windowToken, 0)
            requestData(DataType.FULL)
            fab.show()
        }
        edit_search_deadline.addTextChangedListener {
            if (viewModel.foundData.hasActiveObservers()) {
                viewModel.find(it.toString())
            }
        }
    }


}
