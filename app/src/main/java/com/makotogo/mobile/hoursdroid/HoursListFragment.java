/*
 *     Copyright 2016 Makoto Consulting Group, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package com.makotogo.mobile.hoursdroid;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.makotogo.mobile.framework.AbstractArrayAdapter;
import com.makotogo.mobile.framework.AbstractFragment;
import com.makotogo.mobile.framework.ViewBinder;
import com.makotogo.mobile.hoursdroid.model.DataStore;
import com.makotogo.mobile.hoursdroid.model.Hours;
import com.makotogo.mobile.hoursdroid.model.Job;
import com.makotogo.mobile.hoursdroid.model.Project;
import com.makotogo.mobile.hoursdroid.util.ApplicationOptions;
import com.makotogo.mobile.hoursdroid.util.RoundingUtils;

import org.joda.time.LocalDateTime;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import java.util.Date;
import java.util.List;

/**
 * Created by sperry on 1/12/16.
 */
public class HoursListFragment extends AbstractFragment {

    private static final String TAG = HoursListFragment.class.getSimpleName();

    private static final String STATE_ACTIVE_HOURS = "state." + Hours.class.getName();
    private static final String STATE_FILTER_BEGIN_DATE = "state.begin." + Date.class.getName();
    private static final String STATE_FILTER_END_DATE = "state.end." + Date.class.getName();

    private static final int REQUEST_CODE_MANAGE_PROJECTS = 100;
    private static final int REQUEST_CODE_FILTER_DIALOG = 200;

    private static final boolean IN_ACTION_MODE = true;
    private static final boolean NOT_IN_ACTION_MODE = false;

    /**
     * This is the Job that sets the overall scope
     */
    private transient Job mJob;

    /**
     * This is the Project for which a new Hours record will be started if the
     * user clicks the Start button
     */
    private transient Project mProject;

    /**
     * This is the Hours object that is "active" meaning it has been started
     * but not yet stopped. Note: it may be paused, but if this Fragment gets
     * completely destroyed (under low memory conditions, say), there is really
     * no good way to know that. However, should the Fragment be recreated
     * because, say, the device was rotated, then we want to keep up with that.
     * <p/>
     * Saved to Bundle: yes
     */
    private Hours mActiveHours;

    /**
     * The filter settings. If set, we need to restore them if there is a config
     * change (e.g., device rotation)
     * Saved to Bundle: yes
     */
    private Date mFilterBeginDate;
    private Date mFilterEndDate;

    /**
     * Allows us to ignore regular presses on the list view if we are in Action Mode
     */
    private transient boolean mInActionMode;


    /**
     * Configure the UI.
     */
    @Override
    protected View configureUI(LayoutInflater layoutInflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the view
        View view = layoutInflater.inflate(R.layout.fragment_hours_list, container, false);
        Spinner projectSpinner = (Spinner) view.findViewById(R.id.spinner_hours_list_project);
        configureProjectSpinner(projectSpinner);
        ListView listView = (ListView) view.findViewById(R.id.hours_list_view);
        // Access the Job backing store singleton
        //DataStore dataStore = DataStore.instance(getActivity());
        configureHoursListView(listView);
        // Create Filter listener - REMOVED
        //ImageView filterIcon = (ImageView) view.findViewById(R.id.imageview_hours_list_filter);
        //configureFilterListener(filterIcon);
        // Create Start/Stop button
        configureStartStopButton(view);
        return view;
    }

    @Override
    protected void processFragmentArguments() {
        Bundle arguments = getArguments();
        mJob = (Job) arguments.getSerializable(FragmentFactory.FRAG_ARG_JOB);
        // Sanity check
        if (mJob == null) {
            // Complain. Loudly.
            throw new RuntimeException("Fragment (" + TAG + ") argument (" + FragmentFactory.FRAG_ARG_JOB + ") cannot be null!");
        }
        mProject = fetchProjectToUse(mJob);
    }

    /**
     * Figure out which project to use. There are two cases:
     * 1. There is a project with active Hours. Use that.
     * 2. ELSE There is "last used" project in Shared Preferences. Use that.
     * 3. ELSE Use the Default Project.
     *
     * @param job
     * @return
     */
    private Project fetchProjectToUse(Job job) {
        Project ret = mProject;
        if (mProject == null) {
            DataStore dataStore = DataStore.instance(getActivity());
            ret = dataStore.getDefaultProject(job);// Default
            // If there is a project with active Hours use that.
            mActiveHours = dataStore.getActiveHours(job);
            if (mActiveHours != null) {
                ret = mActiveHours.getProject();
            } else {
                int lastUsedProjectId = ApplicationOptions.instance(getActivity()).getLastUsedProjectId(job.getId());
                Log.d(TAG, "Last project ID Used: " + lastUsedProjectId);
                if (lastUsedProjectId > 0) {
                    ret = dataStore.getProject(lastUsedProjectId);
                    Log.d(TAG, "Using Project: " + ret.getName());
                } else {
                    Log.d(TAG, "No active Hours. Using default project");
                }
            }
        }
        return ret;
    }

    @Override
    public void saveInstanceState(Bundle outState) {
        // Save the Active Hours object
        outState.putSerializable(STATE_ACTIVE_HOURS, mActiveHours);
        outState.putSerializable(STATE_FILTER_BEGIN_DATE, mFilterBeginDate);
        outState.putSerializable(STATE_FILTER_END_DATE, mFilterEndDate);
        updateSharedPreferences();
    }

    @Override
    public void restoreInstanceState(Bundle savedInstanceState) {
        // Restore the Active Hours object
        mActiveHours = (Hours) savedInstanceState.getSerializable(STATE_ACTIVE_HOURS);
        mFilterBeginDate = (Date) savedInstanceState.getSerializable(STATE_FILTER_BEGIN_DATE);
        mFilterEndDate = (Date) savedInstanceState.getSerializable(STATE_FILTER_END_DATE);
    }

    @Override
    protected void updateSharedPreferences() {
        ApplicationOptions applicationOptions = ApplicationOptions.instance(getActivity());
        // Save the last used project
        applicationOptions.saveLastUsedProjectId(mJob.getId(), mProject.getId());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        final String METHOD = "onActivityResult(" + requestCode + ", " + resultCode + ", " + data + "): ";
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_MANAGE_PROJECTS:
                    Project project = (Project) data.getSerializableExtra(ProjectListActivity.RESULT_PROJECT);
                    if (project == null) {
                        project = fetchProjectToUse(mJob);
                    }
                    mProject = project;
                    break;
                case REQUEST_CODE_FILTER_DIALOG:
                    mFilterBeginDate = (Date) data.getSerializableExtra(FilterDialogFragment.RESULT_BEGIN_DATE);
                    mFilterEndDate = (Date) data.getSerializableExtra(FilterDialogFragment.RESULT_END_DATE);
                    break;
                default:
                    String message = "Cannot handle requestCode (" + requestCode + "). User pressed the Back button, maybe?";
                    Log.w(TAG, METHOD + message);
                    break;
            }
            updateUI();
        } else {
            String message = "Cannot handle resultCode (" + resultCode + "). User pressed the Back button, maybe?";
            Log.w(TAG, METHOD + message);
        }
        Log.d(TAG, METHOD + "DONE.");
    }

    /**
     * Update the UI:
     * - Reload the list of Jobs from the DataStore
     * - Update the JobAdapter with the refreshed List
     * - Update the View
     */
    protected void updateUI() {
        DataStore dataStore = DataStore.instance(getActivity());
        // Now refresh the view
        List<Hours> hours = dataStore.getHours(mProject);
        updateHoursListView(hours);
        List<Project> projects = dataStore.getProjects(mJob);
        projects.add(Project.MANAGE_PROJECTS);
        updateProjectSpinner(projects);
        updateStartStopButton();
    }

    private void configureProjectSpinner(final Spinner projectSpinner) {
        projectSpinner.setAdapter(new AbstractArrayAdapter(getActivity(), R.layout.project_list_row) {
            @Override
            protected ViewBinder<Project> createViewBinder() {
                return new ProjectViewBinder();
            }
        });
        projectSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Project project = (Project) projectSpinner.getAdapter().getItem(position);
                if (project == Project.MANAGE_PROJECTS) {
                    // Launch the Project List Screen
                    Intent intent = new Intent(getActivity(), ProjectListActivity.class);
                    intent.putExtra(ProjectListActivity.EXTRA_JOB, mJob);
                    //Toast.makeText(getActivity(), "Launching ProjectListActivity (eventually)...", Toast.LENGTH_LONG).show();
                    startActivityForResult(intent, REQUEST_CODE_MANAGE_PROJECTS);
                } else {
                    // Active project has changed. Update the UI.
                    mProject = project;
                    updateUI();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void updateProjectSpinner(List<Project> projects) {
        Spinner projectSpinner = (Spinner) getView().findViewById(R.id.spinner_hours_list_project);
        AbstractArrayAdapter<Project> projectListAdapter = (AbstractArrayAdapter<Project>) projectSpinner.getAdapter();
        if (projectListAdapter != null) {
            projectListAdapter.clear();
            projectListAdapter.addAll(projects);
            projectListAdapter.notifyDataSetChanged();
            int selectedIndex = 0;
            // Figure out which selection corresponds to the active project
            for (int aa = 0; aa < projectListAdapter.getCount(); aa++) {
                if (projectListAdapter.getItem(aa).equals(mProject)) {
                    selectedIndex = aa;
                    break;
                }
            }
            // Select the active project
            projectSpinner.setSelection(selectedIndex);
        }
    }

    @Override
    protected boolean validate(View view) {
        // TODO: Add validation logic here
        return true;
    }

    /**
     * I really hate stuff like this, but I don't see any better way around it. I want to be
     * able to handle short (regular) and long press on the list view. Unfortunately, both
     * are triggered on a long press, so it's impossible to tell which is which. Except that
     * it *appears* that the Long press is always handled first, which gives us the chance to
     * set some bullshit state variables like this one (yuck yuck yuck).
     * <p/>
     * If the "InActionMode" attribute is true, it indicates that the Activity is currently
     * in Action Mode, which will cause the List View to ignore short/regular press (by checking
     * this state value (again yuck yuck yuck)).
     *
     * @param value
     */
    private void setInActionMode(boolean value) {
        mInActionMode = value;
    }

    private ListView getHoursListView() {
        View view = getView();
        if (view == null) {
            throw new RuntimeException("View has not yet been configured. Cannot invoke getHoursListView()!");
        }
        return (ListView) view.findViewById(R.id.hours_list_view);
    }

    private HoursAdapter getHoursListViewAdapter() {
        HoursAdapter ret = null;
        if (getHoursListView() == null) {
            throw new RuntimeException("ListView has not been configured!");
        }
        ret = (HoursAdapter) getHoursListView().getAdapter();
        return ret;
    }

    private void configureHoursListView(final ListView listView) {
        // Create a new JobAdapter
        listView.setAdapter(new HoursAdapter(getActivity(), R.layout.hours_list_row));
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        // Create the OnItemLongClickListener, used to bring up the contextual
        /// actions for the List View
        createHoursListViewOnItemLongClickListener(listView);
        // Create the
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!mInActionMode) {
                    Hours hours = (Hours) listView.getAdapter().getItem(position);
                    Intent intent = new Intent(getActivity(), HoursDetailActivity.class);
                    intent.putExtra(HoursDetailActivity.EXTRA_HOURS, hours);
                    startActivity(intent);
                }
            }
        });
    }

    private void updateHoursListView(List<Hours> hours) {
        if (getHoursListViewAdapter() != null) {
            getHoursListViewAdapter().clear();
            getHoursListViewAdapter().addAll(hours);
            getHoursListViewAdapter().notifyDataSetChanged();
        }
    }

    private void createHoursListViewOnItemLongClickListener(final ListView listView) {
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                boolean ret = false;
                // If the selected item is the active one, then do nothing (except display
                /// a Toast)
                final Hours hours = (Hours) listView.getAdapter().getItem(position);
                if (!hours.equals(mActiveHours)) {
                    // Long press... It will be handled first. Set the state variable (yuck).
                    setInActionMode(IN_ACTION_MODE);
                    getActivity().startActionMode(new ActionMode.Callback() {
                        @Override
                        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                            // Inflate the menu
                            MenuInflater menuInflater = mode.getMenuInflater();
                            menuInflater.inflate(R.menu.menu_hours_ca, menu);
                            // Must return true here or the ART will abort creation of the menu!
                            return true;
                        }

                        @Override
                        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                            boolean ret;
                            // Switch on the menu item ID
                            switch (item.getItemId()) {
                                case R.id.menu_item_hours_delete:
                                    DataStore.instance(getActivity()).delete(hours);
                                    ret = true;// handled
                                    break;
                                default:
                                    // Not necessary, but included for completeness
                                    ret = false;
                                    break;
                            }
                            mode.finish();
                            // The UI probably needs updating.
                            updateUI();
                            return ret;
                        }

                        @Override
                        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                            return false;
                        }

                        @Override
                        public void onDestroyActionMode(ActionMode mode) {
                            // Through with Action Mode. The list view can go back to handling
                            /// short press.
                            setInActionMode(NOT_IN_ACTION_MODE);
                        }
                    });
                } // End else
                return ret;
            }
        });
    }

    private void configureStartStopButton(View view) {
        final Button startStopButton = (Button) view.findViewById(R.id.button_hours_start_stop);
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Button was clicked. Now what?
                startOrStopHours();
                startStopButton.setText(
                        getActivity().getResources().getText(
                                (isActive()) ? R.string.stop_work : R.string.start_work
                        )
                );
            }
        });
    }

    private void updateStartStopButton() {
        // Handle the button
        if (isActive()) {
            if (getView() != null) {
                Button button = (Button) getView().findViewById(R.id.button_hours_start_stop);
                button.setText(getActivity().getResources().getText(R.string.stop_work));
                button.setEnabled(isActiveHoursProjectSameAsCurrentProject());
            }
        }
    }

    private boolean isActive() {
        return mActiveHours != null;
    }

    private boolean isActiveHoursProjectSameAsCurrentProject() {
        boolean ret = false;
        if (mProject != null && mActiveHours != null) {
            if (mProject.equals(mActiveHours.getProject())) {
                ret = true;
            }
        }
        return ret;
    }

    private void startOrStopHours() {
        DataStore dataStore = DataStore.instance(getActivity());
        if (mActiveHours == null) {
            // No active Hours, start one
            Hours hours = new Hours();
            hours.setBegin(RoundingUtils.calculateDateWithRounding());
            hours.setProject(mProject);
            hours.setJob(mProject.getJob());
            hours.setBilled(false);
            mActiveHours = dataStore.create(hours);
            // Add mActiveHours to the List
        } else {
            // Active hours, stop it now
            mActiveHours.setEnd(RoundingUtils.calculateDateWithRounding());
            dataStore.update(mActiveHours);
            mActiveHours = null;
            updateSharedPreferences();
        }
        // Now update the UI
        updateUI();
    }

    /**
     * Note: do not refactor this class outside of HoursListFragment, because
     * it needs to infer the "active" Hours object, and set a reference to
     * mActiveHours on the parent. Once refactored outside, that is no longer
     * possible, and that inference cannot be made.
     */
    private class HoursViewBinder implements ViewBinder<Hours> {

        private TextView getBeginDate(View view) {
            return (TextView) view.findViewById(R.id.textview_hours_list_row_begin_date);
        }

        private TextView getEndDate(View view) {
            return (TextView) view.findViewById(R.id.textview_hours_list_row_end_date);
        }

        private TextView getBreak(View view) {
            return (TextView) view.findViewById(R.id.textview_hours_list_row_break);
        }

        private TextView getTotal(View view) {
            return (TextView) view.findViewById(R.id.textview_hours_list_row_total);
        }

        private ImageView getActiveHours(View view) {
            return (ImageView) view.findViewById(R.id.imageview_hours_list_row_active_hours);
        }

        private ImageView getBilledHours(View view) {
            return (ImageView) view.findViewById(R.id.imageview_hours_list_row_billed_hours);
        }

        @Override
        public void initView(View view) {
            getBeginDate(view).setText("");
            getEndDate(view).setText("");
            getBreak(view).setText("");
            getTotal(view).setText("");
            getActiveHours(view).setVisibility(View.INVISIBLE);
            getBilledHours(view).setVisibility(View.INVISIBLE);
        }

        @Override
        public void bind(final Hours hours, final View view) {
            // TODO: Move this formatting to AsyncTask?
            final long beginTime = hours.getBegin().getTime();
            final long breakTime = hours.getBreak();
            final String dateTimeFormatString = "M/d/yy h:mm a";
            // Begin Date
            LocalDateTime beginDateTime = new LocalDateTime(beginTime);
            getBeginDate(view).setText(beginDateTime.toString(dateTimeFormatString));
            if (hours.getEnd() == null) {
                // Found the active record
                // NOTE: DO NOT REFACTOR THIS CLASS OUTSIDE OF ITS CONTAINING CLASS!!
                mActiveHours = hours;
                Button startStopButton = (Button) getView().findViewById(R.id.button_hours_start_stop);
                startStopButton.setText(getActivity().getResources().getText(R.string.stop_work));
                // Start the animation for this row
                getActiveHours(view).setVisibility(View.VISIBLE);
            } else {
                String endDateTimeKey = "endDateTime";
                String totalTimeKey = "totalTime";
                String breakTimeKey = "breakTime";
                PeriodFormatter periodFormatter = new PeriodFormatterBuilder()
                        .printZeroNever()
                        //.appendDays().appendSuffix("d")
                        //.appendSeparator(", ")
                        .appendHours().appendSuffix("h")
                        .appendSeparator(": ")
                        .appendMinutes().appendSuffix("m")
                        //.appendSeparator(": ")
                        //.appendSeconds().appendSuffix("s")
                        .toFormatter();
                // End Date
                long endTime = hours.getEnd().getTime();
                LocalDateTime endDateTime = new LocalDateTime(endTime);
                getEndDate(view).setText(endDateTime.toString(dateTimeFormatString));
                // Total
                long elapsedTime = endTime - beginTime - breakTime;
                elapsedTime = RoundingUtils.applyRoundingIfNecessary(getActivity(), elapsedTime);
                Period period = new Period(elapsedTime);
                getTotal(view).setText(periodFormatter.print(period));
                // Break
                period = new Period(breakTime);
                getBreak(view).setText(periodFormatter.print(period));
            }
            if (hours.isBilled()) {
                getBilledHours(view).setVisibility(View.VISIBLE);
            }
        }
    }

    private class HoursAdapter extends AbstractHoursAdapter {

        public HoursAdapter(Context context, int layoutResourceId) {
            // This constructor indicates we will provide our own View inflation
            super(context, layoutResourceId);
        }

        @Override
        protected ViewBinder<Hours> createViewBinder() {
            return new HoursViewBinder();
        }
    }
}

    // A   T   T   I   C
