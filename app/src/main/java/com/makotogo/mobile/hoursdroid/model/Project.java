package com.makotogo.mobile.hoursdroid.model;

import com.makotogo.mobile.framework.ModelObject;

import java.io.Serializable;

/**
 * Created by sperry on 1/12/16.
 */
public class Project implements ModelObject, Serializable {

    public static final Project MANAGE_PROJECTS = new Project("Manage Projects...", "Select to manage projects for this Job.");

    public static final String DEFAULT_PROJECT_NAME = "Default";
    public static final String DEFAULT_PROJECT_DESCRIPTION = "The Default Project";
    private Integer mId;
    private String mName;
    private String mDescription;
    private Job mJob;
    private Boolean mDefaultForJob;

    public Project() {
        // Nothing to do
    }

    public Project(String name, String description) {
        mName = name;
        mDescription = description;
    }

    public Integer getId() {
        return mId;
    }

    public void setId(Integer id) {
        mId = id;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public String getDescription() {
        return mDescription;
    }

    public void setDescription(String description) {
        mDescription = description;
    }

    public Job getJob() {
        return mJob;
    }

    public void setJob(Job job) {
        mJob = job;
    }

    public Boolean getDefaultForJob() {
        return mDefaultForJob;
    }

    public void setDefaultForJob(Boolean defaultForJob) {
        mDefaultForJob = defaultForJob;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Project project = (Project) o;

        if (!getId().equals(project.getId())) return false;
        if (!getName().equals(project.getName())) return false;
        if (!getDescription().equals(project.getDescription())) return false;
        if (!getJob().equals(project.getJob())) return false;
        if (getDefaultForJob() != null ? !getDefaultForJob().equals(project.getDefaultForJob()) : project.getDefaultForJob() != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = getId().hashCode();
        result = 31 * result + getName().hashCode();
        result = 31 * result + getDescription().hashCode();
        result = 31 * result + getJob().hashCode();
        result = 31 * result + (getDefaultForJob() != null ? getDefaultForJob().hashCode() : 0);
        return result;
    }
}
