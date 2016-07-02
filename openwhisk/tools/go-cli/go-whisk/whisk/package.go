/*
 * Copyright 2015-2016 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package whisk

import (
    "fmt"
    "net/http"
    "net/url"
    "errors"
    "reflect"
    "encoding/json"
    "strings"
)

type PackageService struct {
    client *Client
}

type PackageInterface interface {
    GetName() string
}

// Use this struct to create/update a package/binding with the Publish setting
type SentPackagePublish struct {
    Namespace string `json:"-"`
    Name      string `json:"-"`
    Version   string `json:"version,omitempty"`
    Publish   bool   `json:"publish"`
    Annotations 	 `json:"annotations,omitempty"`
    Parameters  	 *json.RawMessage `json:"parameters,omitempty"`
}
func (p *SentPackagePublish) GetName() string {
    return p.Name
}

// Use this struct to update a package/binding with no change to the Publish setting
type SentPackageNoPublish struct {
    Namespace string `json:"-"`
    Name      string `json:"-"`
    Version   string `json:"version,omitempty"`
    Publish   bool   `json:"publish,omitempty"`
    Annotations 	 `json:"annotations,omitempty"`
    Parameters  	 *json.RawMessage `json:"parameters,omitempty"`
}
func (p *SentPackageNoPublish) GetName() string {
    return p.Name
}

// Use this struct to represent the package/binding sent from the Whisk server
// Binding is a bool ???MWD20160602 now seeing Binding as a struct???
type Package struct {
    Namespace string    `json:"namespace,omitempty"`
    Name      string    `json:"name,omitempty"`
    Version   string    `json:"version,omitempty"`
    Publish   bool      `json:"publish"`
    Annotations 	    `json:"annotations,omitempty"`
    Parameters  	    *json.RawMessage `json:"parameters,omitempty"`
    Binding             `json:"binding,omitempty"`
    Actions  []Action   `json:"actions,omitempty"`
    Feeds    []Action   `json:"feeds,omitempty"`
}
func (p *Package) GetName() string {
    return p.Name
}

func (p *Package) GetAnnotationKeyValue(key string) string {
    var val string = ""

    Debug(DbgInfo, "Looking for annotation with key of '%s'\n", key)
    if p.Annotations != nil {
        for i,_ := range p.Annotations {
            Debug(DbgInfo, "Examining annotation %+v\n", p.Annotations[i])
            annotation := p.Annotations[i]
            if k, ok := annotation["key"].(string); ok {
                if k == key {
                    if val, ok := annotation["value"].(string); ok {
                        Debug(DbgInfo, "annotation[%s] = '%s'\n", key, val)
                        if val != "" {
                            return val
                        }
                    } else {
                        Debug(DbgWarn, "Annotation 'value' is not a string type: %s", reflect.TypeOf(annotation["value"]).String())
                    }
                }
            } else {
                Debug(DbgWarn, "Annotation 'key' is not a string type: %s", reflect.TypeOf(annotation["key"]).String())
            }
        }
    }
    return val
}

// Use this struct when creating a binding
// Publish is NOT optional; Binding is a namespace/name object, not a bool
type BindingPackage struct {
    Namespace string `json:"-"`
    Name      string `json:"-"`
    Version   string `json:"version,omitempty"`
    Publish   bool   `json:"publish"`
    Annotations 	 `json:"annotations,omitempty"`
    Parameters  	 *json.RawMessage `json:"parameters,omitempty"`
    Binding          `json:"binding"`
}
func (p *BindingPackage) GetName() string {
    return p.Name
}

type Binding struct {
    Namespace string `json:"namespace,omitempty"`
    Name      string `json:"name,omitempty"`
}

type BindingUpdates struct {
    //Added   []Binding `json:"added,omitempty"`
    //Updated []Binding `json:"updated,omitempty"`
    //Deleted []Binding `json:"deleted,omitempty"`
    Added   []string `json:"added,omitempty"`
    Updated []string `json:"updated,omitempty"`
    Deleted []string `json:"deleted,omitempty"`
}

type PackageListOptions struct {
    Public bool `url:"public,omitempty"`
    Limit  int  `url:"limit"`
    Skip   int  `url:"skip"`
    Since  int  `url:"since,omitempty"`
    Docs   bool `url:"docs,omitempty"`
}

func (s *PackageService) List(options *PackageListOptions) ([]Package, *http.Response, error) {
    route := fmt.Sprintf("packages")
    route, err := addRouteOptions(route, options)
    if err != nil {
        Debug(DbgError, "addRouteOptions(%s, %#v) error: '%s'\n", route, options, err)
        errStr := fmt.Sprintf("Unable to build request URL: error: %s", err)
        werr := MakeWskErrorFromWskError(errors.New(errStr), err, EXITCODE_ERR_GENERAL, DISPLAY_MSG, NO_DISPLAY_USAGE)
        return nil, nil, werr
    }

    req, err := s.client.NewRequest("GET", route, nil)
    if err != nil {
        Debug(DbgError, "http.NewRequest(GET, %s); error '%s'\n", route, err)
        errStr := fmt.Sprintf("Unable to create GET HTTP request for '%s'; error: %s", route, err)
        werr := MakeWskErrorFromWskError(errors.New(errStr), err, EXITCODE_ERR_GENERAL, DISPLAY_MSG, NO_DISPLAY_USAGE)
        return nil, nil, werr
    }

    var packages []Package
    resp, err := s.client.Do(req, &packages)
    if err != nil {
        Debug(DbgError, "s.client.Do() error - HTTP req %s; error '%s'\n", req.URL.String(), err)
        errStr := fmt.Sprintf("Request failure: %s", err)
        werr := MakeWskErrorFromWskError(errors.New(errStr), err, EXITCODE_ERR_NETWORK, DISPLAY_MSG, NO_DISPLAY_USAGE)
        return nil, resp, werr
    }

    return packages, resp, err

}

func (s *PackageService) Get(packageName string) (*Package, *http.Response, error) {
    route := fmt.Sprintf("packages/%s", strings.Replace(url.QueryEscape(packageName), "+", " ", -1))

    req, err := s.client.NewRequest("GET", route, nil)
    if err != nil {
        Debug(DbgError, "http.NewRequest(GET, %s); error '%s'\n", route, err)
        errStr := fmt.Sprintf("Unable to create GET HTTP request for '%s'; error: %s", route, err)
        werr := MakeWskErrorFromWskError(errors.New(errStr), err, EXITCODE_ERR_GENERAL, DISPLAY_MSG, NO_DISPLAY_USAGE)
        return nil, nil, werr
    }

    p := new(Package)
    resp, err := s.client.Do(req, &p)
    if err != nil {
        Debug(DbgError, "s.client.Do() error - HTTP req %s; error '%s'\n", req.URL.String(), err)
        errStr := fmt.Sprintf("Request failure: %s", err)
        werr := MakeWskErrorFromWskError(errors.New(errStr), err, EXITCODE_ERR_NETWORK, DISPLAY_MSG, NO_DISPLAY_USAGE)
        return nil, resp, werr
    }

    return p, resp, nil

}

func (s *PackageService) Insert(x_package PackageInterface, overwrite bool) (*Package, *http.Response, error) {
    route := fmt.Sprintf("packages/%s?overwrite=%t",
        strings.Replace(url.QueryEscape(x_package.GetName()), "+", " ", -1), overwrite)

    req, err := s.client.NewRequest("PUT", route, x_package)
    if err != nil {
        Debug(DbgError, "http.NewRequest(PUT, %s); error '%s'\n", route, err)
        errStr := fmt.Sprintf("Unable to create PUT HTTP request for '%s'; error: %s", route, err)
        werr := MakeWskErrorFromWskError(errors.New(errStr), err, EXITCODE_ERR_GENERAL, DISPLAY_MSG, NO_DISPLAY_USAGE)
        return nil, nil, werr
    }

    p := new(Package)
    resp, err := s.client.Do(req, &p)
    if err != nil {
        Debug(DbgError, "s.client.Do() error - HTTP req %s; error '%s'\n", req.URL.String(), err)
        errStr := fmt.Sprintf("Request failure: %s", err)
        werr := MakeWskErrorFromWskError(errors.New(errStr), err, EXITCODE_ERR_NETWORK, DISPLAY_MSG, NO_DISPLAY_USAGE)
        return nil, resp, werr
    }

    return p, resp, nil
}

func (s *PackageService) Delete(packageName string) (*http.Response, error) {
    route := fmt.Sprintf("packages/%s", strings.Replace(url.QueryEscape(packageName), "+", " ", -1))

    req, err := s.client.NewRequest("DELETE", route, nil)
    if err != nil {
        Debug(DbgError, "http.NewRequest(DELETE, %s); error '%s'\n", route, err)
        errStr := fmt.Sprintf("Unable to create DELETE HTTP request for '%s'; error: %s", route, err)
        werr := MakeWskErrorFromWskError(errors.New(errStr), err, EXITCODE_ERR_GENERAL, DISPLAY_MSG, NO_DISPLAY_USAGE)
        return nil, werr
    }

    resp, err := s.client.Do(req, nil)
    if err != nil {
        Debug(DbgError, "s.client.Do() error - HTTP req %s; error '%s'\n", req.URL.String(), err)
        errStr := fmt.Sprintf("Request failure: %s", err)
        werr := MakeWskErrorFromWskError(errors.New(errStr), err, EXITCODE_ERR_NETWORK, DISPLAY_MSG, NO_DISPLAY_USAGE)
        return resp, werr
    }

    return resp, nil
}

func (s *PackageService) Refresh() (*BindingUpdates, *http.Response, error) {
    route := "packages/refresh"

    req, err := s.client.NewRequest("POST", route, nil)
    if err != nil {
        Debug(DbgError, "http.NewRequest(POST, %s); error '%s'\n", route, err)
        errStr := fmt.Sprintf("Unable to create POST HTTP request for '%s'; error: %s", route, err)
        werr := MakeWskErrorFromWskError(errors.New(errStr), err, EXITCODE_ERR_GENERAL, DISPLAY_MSG, NO_DISPLAY_USAGE)
        return nil, nil, werr
    }

    updates := &BindingUpdates{}
    resp, err := s.client.Do(req, updates)
    if err != nil {
        Debug(DbgError, "s.client.Do() error - HTTP req %s; error '%s'\n", req.URL.String(), err)
        errStr := fmt.Sprintf("Request failure: %s", err)
        werr := MakeWskErrorFromWskError(errors.New(errStr), err, EXITCODE_ERR_NETWORK, DISPLAY_MSG, NO_DISPLAY_USAGE)
        return nil, resp, werr
    }

    return updates, resp, nil
}
