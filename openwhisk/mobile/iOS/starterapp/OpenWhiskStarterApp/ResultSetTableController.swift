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


import UIKit

class ResultSetTableController: UITableViewController {
    
    var resultSet: [String: AnyObject]!
    var names = [String]()
    var values = [String]()
    var isComplex = [Bool]()
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        
        tableView.estimatedRowHeight = 44
        tableView.rowHeight = UITableViewAutomaticDimension
        
        navigationItem.title = "Action Results"
        
        reloadTable()
    }
    
    func reloadTable() {
        
        names = [String]()
        values = [String]()
        
        if let resultSet = resultSet where resultSet.count > 0 {
            for (name, value) in resultSet {
                names.append(name)
                
                if let value = (value as? String) {
                    values.append(value)
                    isComplex.append(false)
                } else {
                    if value is Bool {
                        if (value as! Bool) == true {
                            values.append("true")
                        } else {
                            values.append("false")
                        }
                        isComplex.append(false)
                    } else if value is NSNumber {
                        values.append(value.stringValue)
                        isComplex.append(false)
                    } else {
                        do {
                            let theJSONData = try NSJSONSerialization.dataWithJSONObject(
                                value ,
                                options: NSJSONWritingOptions(rawValue: 0))
                            let theJSONText = NSString(data: theJSONData,
                                encoding: NSASCIIStringEncoding)
                            values.append(theJSONText as! String)
                            isComplex.append(true)
                        } catch {
                            print("Error converting value to JSON")
                            values.append("Unknown type")
                            isComplex.append(false)
                        }
                    }
                }
            }
        }
        
        
    }
    
    
    // MARK UITableViewDataSource
    override func tableView(tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return resultSet.count
    }
    
    override func tableView(tableView: UITableView, cellForRowAtIndexPath indexPath: NSIndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCellWithIdentifier("resultCell") as! ResultSetCell
        
        cell.nameLabel.text = names[indexPath.row]
        cell.valueLabel.text = values[indexPath.row]
        
        if isComplex[indexPath.row] == true {
            cell.accessoryType = UITableViewCellAccessoryType.DisclosureIndicator
        }
        
        
        return cell
        
    }
    
    
    
}
