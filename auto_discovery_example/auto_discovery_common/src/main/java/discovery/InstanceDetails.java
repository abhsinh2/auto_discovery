package discovery;

import com.fasterxml.jackson.annotation.JsonRootName;

/**
 * Created by abhsinh2 on 21/07/16.
 */
@JsonRootName("details")
public class InstanceDetails {
    private String description;

    public InstanceDetails() {
        this("");
    }

    public InstanceDetails(String description) {
        this.description = description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
