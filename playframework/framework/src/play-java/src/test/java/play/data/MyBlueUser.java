/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.data;

import play.data.validation.Constraints.ValidateWith;

public class MyBlueUser {
    public String name;

    @ValidateWith(BlueValidator.class)
    private String skinColor;
    
    @ValidateWith(value=BlueValidator.class, message="i-am-blue")
    private String hairColor;
    
    public String getSkinColor() {
        return skinColor;
    }
    
    public void setSkinColor(String value) {
        skinColor = value;
    }
    
    public String getHairColor() {
        return hairColor;
    }
    
    public void setHairColor(String value) {
        hairColor = value;
    }
}
